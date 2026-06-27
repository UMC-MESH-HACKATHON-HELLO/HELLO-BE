package com.mesh.hello.domain.calling.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesh.hello.domain.calling.domain.CallSummary;
import com.mesh.hello.domain.calling.repository.CallSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BedrockSummaryService {

    private static final String MODEL_ID = "anthropic.claude-3-5-sonnet-20240620-v1:0";

    private final BedrockRuntimeClient bedrockRuntimeClient;
    private final CallSummaryRepository callSummaryRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Async
    public void summarizeAndSave(String roomId, String transcript) {
        if (transcript == null || transcript.isBlank()) {
            return;
        }
        try {
            String summary = callBedrock(transcript);
            callSummaryRepository.save(new CallSummary(roomId, transcript, summary));
            log.info("통화 요약 저장 완료 roomId={}", roomId);
        } catch (Exception e) {
            log.error("통화 요약 실패 roomId={}", roomId, e);
        }
    }

    private String callBedrock(String transcript) throws Exception {
        String prompt = "다음은 어르신과 도우미의 통화 내용입니다. 통화의 주요 내용을 2~3문장으로 간결하게 요약해주세요.\n\n"
                + transcript;

        Map<String, Object> requestBody = Map.of(
                "anthropic_version", "bedrock-2023-05-31",
                "max_tokens", 500,
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                )
        );

        String requestJson = objectMapper.writeValueAsString(requestBody);

        InvokeModelRequest request = InvokeModelRequest.builder()
                .modelId(MODEL_ID)
                .contentType("application/json")
                .accept("application/json")
                .body(SdkBytes.fromUtf8String(requestJson))
                .build();

        InvokeModelResponse response = bedrockRuntimeClient.invokeModel(request);
        String responseJson = response.body().asUtf8String();

        JsonNode root = objectMapper.readTree(responseJson);
        return root.path("content").get(0).path("text").asText();
    }
}