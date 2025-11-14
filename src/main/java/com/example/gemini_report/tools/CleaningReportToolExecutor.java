package com.example.gemini_report.tools;

import com.example.gemini_report.entity.CleaningData; // CleaningData 엔티티 클래스 임포트
import com.example.gemini_report.service.CleaningDataService; // CleaningDataService 서비스 임포트
import com.fasterxml.jackson.core.JsonProcessingException; // JSON 처리 중 발생할 수 있는 예외 임포트
import com.fasterxml.jackson.databind.ObjectMapper; // JSON 직렬화/역직렬화를 위한 ObjectMapper 임포트
import com.google.common.collect.ImmutableList; // 불변 리스트 생성을 위한 Guava ImmutableList 임포트
import com.google.common.collect.ImmutableMap; // 불변 맵 생성을 위한 Guava ImmutableMap 임포트
import com.google.genai.types.*; // Gemini API 관련 타입들 임포트 (FunctionDeclaration, Schema, Type 등)
import lombok.RequiredArgsConstructor; // Lombok 어노테이션으로 생성자 자동 생성
import org.springframework.stereotype.Component; // Spring 컴포넌트임을 나타내는 어노테이션

import java.util.List; // List 인터페이스 임포트
import java.util.Map; // Map 인터페이스 임포트

/**
 * {@code CleaningReportToolExecutor}는 청소 데이터 리포트 생성 기능을 담당하는 {@link ToolExecutor} 구현체입니다.
 * 이 클래스는 Gemini 모델이 "get_cleaning_report" 함수를 호출하도록 제안할 때,
 * 실제 청소 데이터를 조회하고 그 결과를 JSON 형태로 모델에 반환하는 역할을 수행합니다.
 * <p>
 * 이 클래스는 {@code @Component} 어노테이션을 통해 Spring 빈으로 등록되며,
 * {@code @RequiredArgsConstructor}를 통해 final 필드에 대한 생성자 주입을 자동으로 처리합니다.
 * <p>
 * 새로운 도구를 추가하려면 이 클래스와 유사하게 {@link ToolExecutor} 인터페이스를 구현하고,
 * {@link ToolRegistry}에 의해 감지되도록 Spring 컴포넌트로 등록해야 합니다.
 */
@Component
@RequiredArgsConstructor
public class CleaningReportToolExecutor implements ToolExecutor {
    // 이 도구의 고유한 이름. Gemini 모델이 함수 호출을 제안할 때 이 이름을 사용합니다.
    // 다른 도구와 충돌하지 않도록 유일해야 합니다.
    public static final String GET_CLEANING_REPORT = "get_cleaning_report";

    // 청소 데이터 관련 비즈니스 로직을 처리하는 서비스. 실제 데이터 조회는 이 서비스에 위임합니다.
    private final CleaningDataService cleaningDataService;
    // 자바 객체를 JSON 문자열로 변환하거나 그 반대로 변환하는 데 사용됩니다.
    // 도구 실행 결과를 Gemini 모델에 전달하기 위해 JSON 직렬화가 필요합니다.
    private final ObjectMapper objectMapper;

    /**
     * 이 도구의 이름을 반환합니다.
     * {@inheritDoc}
     */
    @Override
    public String getToolName() {
        return GET_CLEANING_REPORT;
    }

    /**
     * 이 도구에 대한 {@link FunctionDeclaration} 객체를 반환합니다.
     * Gemini 모델에게 이 도구의 이름, 설명, 그리고 필요한 매개변수(startDate, endDate)를 알려줍니다.
     * {@inheritDoc}
     */
    @Override
    public FunctionDeclaration getFunctionDeclaration() {
        return FunctionDeclaration.builder()
                .name(GET_CLEANING_REPORT) // 도구의 이름
                .description("지정된 기간 동안의 청소 데이터를 가져옵니다.") // 도구의 기능 설명
                .parameters(
                        Schema.builder()
                                .type(Type.Known.OBJECT) // 매개변수는 JSON 객체 형태
                                .properties(
                                        ImmutableMap.of(
                                                "startDate", Schema.builder().type(Type.Known.STRING).description("시작일 (YYYY-MM-DD 형식)").build(), // 시작일 매개변수
                                                "endDate", Schema.builder().type(Type.Known.STRING).description("종료일 (YYYY-MM-DD 형식)").build() // 종료일 매개변수
                                        )
                                )
                                .required(ImmutableList.of("startDate", "endDate")) // startDate와 endDate는 필수 매개변수
                                .build()
                )
                .build();
    }

    /**
     * Gemini 모델로부터 받은 인자들을 사용하여 청소 데이터를 조회하고 결과를 JSON 문자열로 반환합니다.
     * {@inheritDoc}
     *
     * @param args Gemini 모델이 함수 호출 시 제공한 인자들의 맵. "startDate"와 "endDate"를 포함합니다.
     * @return 조회된 청소 데이터 목록의 JSON 문자열 표현.
     * @throws RuntimeException JSON 변환 중 오류가 발생할 경우.
     */
    @Override
    public String execute(Map<String, Object> args) {
        // 인자 맵에서 startDate와 endDate를 추출합니다.
        String startDate = (String) args.get("startDate");
        String endDate = (String) args.get("endDate");

        // CleaningDataService를 통해 실제 청소 데이터를 조회합니다.
        List<CleaningData> result = cleaningDataService.get_cleaning_report(startDate, endDate);
        try {
            // 조회된 List<CleaningData> 객체를 JSON 문자열로 변환하여 반환합니다.
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            // JSON 변환 중 오류 발생 시 RuntimeException을 발생시킵니다.
            throw new RuntimeException("Error converting cleaning data to JSON", e);
        }
    }

    /**
     * 도구 실행 후 Gemini 모델에 다시 전달할 템플릿화된 프롬프트를 반환합니다.
     * 이 프롬프트는 모델이 함수 실행 결과를 바탕으로 최종 리포트를 생성하도록 유도합니다.
     * {@inheritDoc}
     *
     * @param originalPrompt 사용자가 처음에 Gemini 모델에 보낸 원본 프롬프트.
     * @return 모델에 전달할 템플릿화된 프롬프트 문자열.
     */
    @Override
    public String getTemplatedPrompt(String originalPrompt) {
        return String.format("""
                다음 요청에 따라 리포트를 생성해 주세요.
                리포트 작성 규칙:
                1. 리포트 형식은 마크다운 문법을 사용해서 줄바꿈이나 단을 들여 쓰고 보기 좋게 구성합니다.
                2. 제공된 데이터를 기준으로 청소 요약을 작성합니다.
            
                원본 요청:
                %s
                """, originalPrompt);
    }

    /**
     * 이 도구에 특화된 시스템 지침(System Instruction)을 반환합니다.
     * 이 지침은 Gemini 모델이 청소 리포트와 관련된 작업을 수행할 때
     * 데이터 분석 및 리포팅 전문가의 역할을 하도록 유도합니다.
     * {@inheritDoc}
     *
     * @return 이 도구에 대한 {@link Content} 형태의 시스템 지침.
     */
    @Override
    public Content getSystemInstruction() {
        return Content.fromParts(Part.fromText("너는 데이터 분석과 리포팅 전문가야."));
    }
}
