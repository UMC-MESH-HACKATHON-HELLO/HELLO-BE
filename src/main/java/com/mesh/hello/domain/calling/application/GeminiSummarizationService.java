package com.mesh.hello.domain.calling.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesh.hello.domain.calling.domain.CallSummary;
import com.mesh.hello.domain.calling.repository.CallSummaryRepository;
import com.mesh.hello.global.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiSummarizationService {

    private static final String MODEL_ID = "gemini-2.5-flash-lite";

    private final RestClient geminiRestClient;
    private final CallSummaryRepository callSummaryRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api-key}")
    private String apiKey;

    @Async
    @Transactional
    public void summarizeAndNotify(String roomId, String helpeeSessionId,
                                   String helperSessionId, String transcript) {
        if (transcript == null || transcript.isBlank()) {
            log.info("요약 생략 — 텍스트 없음 (room: {})", roomId);
            return;
        }

        try {
            JsonNode summary = callGemini(transcript);

            String requestedHelp = summary.path("requestedHelp").asText();
            String providedHelp  = summary.path("providedHelp").asText();
            String result        = summary.path("result").asText();
            String fullSummary   = summary.toString();

            callSummaryRepository.save(
                    new CallSummary(roomId, helpeeSessionId, helperSessionId, transcript, fullSummary)
            );

            // 도우미: 전체 요약
            messagingTemplate.convertAndSendToUser(
                    helperSessionId, "/queue/signal",
                    ApiResponse.ok("통화 요약이 완료되었습니다.",
                            Map.of("type", "CALL_SUMMARY",
                                    "requestedHelp", requestedHelp,
                                    "providedHelp", providedHelp,
                                    "result", result))
            );

            // 어르신: 도우미가 제공한 도움만
            messagingTemplate.convertAndSendToUser(
                    helpeeSessionId, "/queue/signal",
                    ApiResponse.ok("통화 요약이 완료되었습니다.",
                            Map.of("type", "CALL_SUMMARY",
                                    "providedHelp", providedHelp))
            );

            log.info("통화 요약 완료 (room: {})", roomId);

        } catch (Exception e) {
            log.error("통화 요약 실패 (room: {})", roomId, e);
        }
    }

    private JsonNode callGemini(String transcript) throws Exception {
        // Gemini generateContent 요청 형식
        Map<String, Object> requestBody = Map.of(
                "contents", new Object[]{
                        Map.of("parts", new Object[]{
                                Map.of("text", buildPrompt(transcript))
                        })
                },
                "generationConfig", Map.of("maxOutputTokens", 1024)
        );

        String responseBody = geminiRestClient.post()
                .uri("/models/{model}:generateContent", MODEL_ID)
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);

        // Gemini 응답 형식: candidates[0].content.parts[0].text
        String responseText = objectMapper.readTree(responseBody)
                .path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();

        String jsonStr = responseText.replaceAll("(?s)```json\\s*|```", "").trim();
        return objectMapper.readTree(jsonStr);
    }

    private String buildPrompt(String transcript) {
        return """
                아래는 어르신과 도우미 간의 통화 내용입니다.

                %s

                위 통화 내용을 분석해서 반드시 아래 JSON 형식으로만 응답하세요. 다른 텍스트는 포함하지 마세요.

                작성 기준:
                - requestedHelp: 어르신이 요청한 도움을 한 문장으로 간결하게 작성하세요.
                - providedHelp: 어르신이 직접 읽을 메뉴얼입니다. 각 단계를 짧고 명확한 행동 지시문으로 작성하세요. "~하기", "~누르기", "~선택" 같은 형식으로 10단어 이내로 작성하세요. 설명체(~했습니다, ~안내드렸습니다)는 절대 사용하지 마세요.
                - result: "해결" 또는 "미해결" 중 하나로만 작성하세요.

                {
                  "requestedHelp": "어르신이 요청한 도움 내용",
                  "providedHelp": "1. 행동 지시\\n2. 행동 지시\\n3. 행동 지시\\n...",
                  "result": "해결 또는 미해결"
                }
                """.formatted(transcript);
    }
}