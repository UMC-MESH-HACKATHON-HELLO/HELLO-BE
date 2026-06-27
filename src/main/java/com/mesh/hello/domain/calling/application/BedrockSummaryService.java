package com.mesh.hello.domain.calling.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private static final String MODEL_ID = "anthropic.claude-3-haiku-20240307-v1:0";

    private final BedrockRuntimeClient bedrockClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String summarize(String transcript, boolean isReport) {
        String prompt = isReport
                ? """
                  아래는 신고된 통화 내용입니다.
                  문제가 된 발언을 중심으로 2~3문장으로 요약해주세요.
                  금전 요구, 욕설, 부적절한 유도가 있다면 반드시 명시해주세요.

                  [통화 내용]
                  %s
                  [요약]
                  """.formatted(transcript)
                : """
                  아래는 어르신과 도우미 간의 안부 통화 내용입니다.
                  통화의 핵심 내용을 2~3문장으로 요약해주세요.

                  [통화 내용]
                  %s
                  [요약]
                  """.formatted(transcript);

        try {
            String requestJson = objectMapper.writeValueAsString(Map.of(
                    "anthropic_version", "bedrock-2023-05-31",
                    "max_tokens", 300,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            ));

            InvokeModelResponse response = bedrockClient.invokeModel(
                    InvokeModelRequest.builder()
                            .modelId(MODEL_ID)
                            .body(SdkBytes.fromUtf8String(requestJson))
                            .build()
            );

            JsonNode root = objectMapper.readTree(response.body().asUtf8String());
            return root.path("content").get(0).path("text").asText();

        } catch (Exception e) {
            log.error("[Bedrock] 요약 실패: {}", e.getMessage());
            return "요약 생성 실패";
        }
    }
}