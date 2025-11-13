package com.example.gemini_report.service;

import com.example.gemini_report.dto.AgentRequest;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;

@Service
@RequiredArgsConstructor
public class AgentService {

    private final Client geminiClient;

    public Mono<String> getReport(AgentRequest request) {
        return Mono.fromCallable(() -> {
            // Get the method reference for automatic function calling
            Method functionMethod = CleaningDataService.class.getMethod(
                    "get_cleaning_report", String.class, String.class);

            // Create the tool
            Tool tool = Tool.builder().functions(functionMethod).build();

            // Create the config
            GenerateContentConfig config = GenerateContentConfig.builder()
                    .tools(tool)
                    .build();

            String newPrompt = String.format("""
                    다음 요청에 따라 리포트를 생성해 주세요.
                    리포트 작성 규칙:
                    1. 리포트 형식은 마크다운 문법을 사용해서 줄바꿈이나 단을 들여 쓰고 보기 좋게 구성합니다.
                    2. 제공된 데이터를 기준으로 청소 요약을 작성합니다.

                    원본 요청:
                    %s
                    """, request.getPrompt());

            // Call the model with the prompt and config
            GenerateContentResponse response = geminiClient.models.generateContent(
                    "gemini-2.5-flash",
                    newPrompt,
                    config);

            return response.text();
        }).doOnError(e -> {
            System.err.println("Error during Gemini API call: " + e.getMessage());
        });
    }
}
