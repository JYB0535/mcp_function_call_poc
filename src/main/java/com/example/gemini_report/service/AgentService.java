package com.example.gemini_report.service;

import com.example.gemini_report.config.GeminiConfigFactory;
import com.example.gemini_report.dto.AgentRequest;
import com.example.gemini_report.tools.ToolExecutor; // ToolExecutor 인터페이스 임포트
import com.example.gemini_report.tools.ToolRegistry; // ToolRegistry 클래스 임포트
import com.fasterxml.jackson.databind.ObjectMapper; // JSON 직렬화/역직렬화를 위한 ObjectMapper 임포트
import com.google.genai.Client; // Gemini API 클라이언트 임포트
import com.google.genai.types.*; // Gemini API 관련 타입들 임포트 (GenerateContentConfig, GenerateContentResponse 등)
import lombok.RequiredArgsConstructor; // Lombok 어노테이션으로 생성자 자동 생성
import org.springframework.stereotype.Service; // Spring 서비스 컴포넌트임을 나타내는 어노테이션
import reactor.core.publisher.Mono; // 비동기 처리를 위한 Reactor Mono 임포트
import com.google.common.collect.ImmutableMap; // 불변 맵 생성을 위한 Guava ImmutableMap 임포트
import java.util.List; // List 인터페이스 임포트
import java.util.Map; // Map 인터페이스 임포트
import java.util.Optional; // Optional 클래스 임포트

/**
 * {@code AgentService}는 사용자 요청을 받아 Gemini 모델과 상호작용하여 리포트를 생성하는 핵심 서비스입니다.
 * 이 서비스는 도구(Tool) 호출 기능을 활용하여 동적으로 데이터를 조회하고,
 * 조회된 데이터를 기반으로 Gemini 모델이 최종 리포트를 생성하도록 지시합니다.
 * <p>
 * 주요 기능:
 * <ul>
 *     <li>사용자 프롬프트와 등록된 도구를 기반으로 Gemini 모델에 콘텐츠 생성 요청</li>
 *     <li>Gemini 모델의 응답에서 함수 호출(Function Call)을 감지 및 처리</li>
 *     <li>감지된 함수 호출에 따라 적절한 {@link ToolExecutor}를 찾아 실행</li>
 *     <li>도구 실행 결과를 다시 Gemini 모델에 전달하여 최종 응답 생성 유도</li>
 *     <li>{@link GeminiConfigFactory}를 통해 Gemini 모델 설정(안전 설정, 시스템 지침 등)을 관리</li>
 *     <li>{@link ToolRegistry}를 통해 사용 가능한 모든 도구를 중앙에서 관리 및 조회</li>
 * </ul>
 * <p>
 * 이 클래스는 {@code @Service} 어노테이션을 통해 Spring의 서비스 계층 컴포넌트로 등록되며,
 * {@code @RequiredArgsConstructor}를 통해 final 필드에 대한 생성자 주입을 자동으로 처리합니다.
 */
@Service
@RequiredArgsConstructor
public class AgentService {
    // JSON 직렬화/역직렬화를 위한 ObjectMapper 인스턴스.
    // 도구 실행 결과(자바 객체)를 Gemini 모델이 이해할 수 있는 JSON 문자열로 변환하는 데 사용됩니다.
    private final ObjectMapper objectMapper;
    // Gemini API와 통신하기 위한 클라이언트 인스턴스.
    private final Client geminiClient;
    // 청소 데이터 관련 비즈니스 로직을 처리하는 서비스.
    // ToolExecutor 구현체에서 이 서비스를 사용하여 실제 데이터를 조회합니다.
    private final CleaningDataService cleaningDataService;
    // Gemini 모델의 콘텐츠 생성 설정(GenerateContentConfig)을 생성하는 팩토리 클래스.
    // 안전 설정, 시스템 지침, 사용 가능한 도구 목록 등을 캡슐화하여 GenerateContentConfig를 빌드합니다.
    private final GeminiConfigFactory geminiConfigFactory;
    // 시스템에 등록된 모든 도구(ToolExecutor)를 관리하고 조회하는 레지스트리 클래스.
    // Gemini 모델에 제공할 FunctionDeclaration 목록을 제공하고, 함수 호출 시 실행할 ToolExecutor를 찾아줍니다.
    private final ToolRegistry toolRegistry;

    // Gemini 모델 호출에 사용될 모델 이름. 현재는 "gemini-2.5-flash"로 고정되어 있습니다.
    private static final String USED_LLM_MODEL = "gemini-2.5-flash";

    /**
     * 사용자 요청을 처리하고 Gemini 모델을 사용하여 리포트를 생성합니다.
     * 이 메서드는 다음 단계를 포함합니다:
     * <ol>
     *     <li>{@link ToolRegistry}에서 현재 시스템에 등록된 모든 도구 목록을 가져옵니다.</li>
     *     <li>가져온 도구 목록과 기본 설정을 사용하여 {@link GeminiConfigFactory}를 통해 {@link GenerateContentConfig}를 생성합니다.</li>
     *     <li>사용자 프롬프트와 생성된 설정을 사용하여 Gemini 모델에 첫 번째 콘텐츠 생성 요청을 보냅니다.</li>
     *     <li>Gemini 모델의 응답을 분석하여 함수 호출(Function Call)이 있는지 확인합니다.</li>
     *     <li>함수 호출이 감지되면:
     *         <ul>
     *             <li>호출된 함수 이름에 해당하는 {@link ToolExecutor}를 {@link ToolRegistry}에서 찾습니다.</li>
     *             <li>함수 호출에 포함된 인자들을 추출하여 {@link ToolExecutor}를 실행합니다.</li>
     *             <li>{@link ToolExecutor}의 실행 결과(JSON 문자열)를 가져옵니다.</li>
     *             <li>{@link ToolExecutor}에서 제공하는 템플릿화된 프롬프트와 함수 실행 결과를 포함하여 Gemini 모델에 두 번째 콘텐츠 생성 요청을 보냅니다.</li>
     *             <li>두 번째 요청에 대한 Gemini 모델의 최종 응답 텍스트를 반환합니다.</li>
     *         </ul>
     *     </li>
     *     <li>함수 호출이 없거나 처리 중 문제가 발생하면, Gemini 모델의 첫 번째 응답 텍스트를 반환합니다.</li>
     * </ol>
     *
     * @param request 사용자 요청 정보를 담고 있는 {@link AgentRequest} 객체 (주로 사용자 프롬프트 포함).
     * @return Gemini 모델로부터 생성된 리포트 텍스트를 포함하는 {@link Mono<String>} 객체.
     */
    public Mono<String> getReport(AgentRequest request) {
        return Mono.fromCallable(() -> {

            // 1. ToolRegistry에서 현재 등록된 모든 도구(Tool) 목록을 가져옵니다.
            //    이 목록은 Gemini 모델에 어떤 함수들을 호출할 수 있는지 알려주는 역할을 합니다.
            List<Tool> registeredTools = toolRegistry.getAllTools();

            // 2. GeminiConfigFactory를 사용하여 Gemini 모델의 콘텐츠 생성 설정을 생성합니다.
            //    이 설정에는 안전 설정, 시스템 지침, 그리고 위에서 가져온 도구 목록이 포함됩니다.
            GenerateContentConfig config = geminiConfigFactory.createDefaultGenerateContentConfig(registeredTools);

            // 3. Gemini 모델에 첫 번째 콘텐츠 생성 요청을 보냅니다.
            //    모델은 사용자 프롬프트와 제공된 도구 목록을 기반으로 응답을 생성합니다.
            //    이 응답에는 텍스트 응답 또는 함수 호출 제안이 포함될 수 있습니다.
            GenerateContentResponse response = geminiClient.models.generateContent(
                    USED_LLM_MODEL, // 사용할 LLM 모델 지정
                    request.getPrompt(), // 사용자 원본 프롬프트
                    config); // 생성된 설정 객체

            // 4. Gemini 모델의 응답에서 함수 호출(Function Call)이 있는지 확인하고 처리합니다.
            //    findFunctionCall 헬퍼 메서드를 사용하여 응답 내에서 첫 번째 FunctionCall을 추출합니다.
            return findFunctionCall(response)
                    // FunctionCall이 존재하면 다음 맵핑 로직을 실행합니다.
                    .map(functionCall -> {
                        // 호출된 함수의 이름을 추출합니다. 이름이 없으면 null을 반환합니다.
                        String functionName = functionCall.name().orElse(null);
                        if (functionName == null) {
                            // 함수 이름이 유효하지 않으면, 첫 번째 Gemini 응답 텍스트를 반환하고 종료합니다.
                            return response.text();
                        }

                        // ToolRegistry에서 추출된 함수 이름에 해당하는 ToolExecutor를 찾습니다.
                        // ToolExecutor는 실제 함수 실행 로직을 캡슐화합니다.
                        ToolExecutor executor = toolRegistry.getToolExecutor(functionName);
                        if (executor == null) {
                            // 해당하는 ToolExecutor를 찾을 수 없으면, 첫 번째 Gemini 응답 텍스트를 반환하고 종료합니다.
                            System.err.println("Error: No ToolExecutor found for function: " + functionName);
                            return response.text();
                        }

                        // 함수 호출에 포함된 인자(arguments)들을 추출합니다.
                        // 인자가 없으면 빈 ImmutableMap을 사용합니다.
                        Map<String, Object> args = functionCall.args().orElse(ImmutableMap.of());

                        // 찾은 ToolExecutor를 사용하여 실제 함수를 실행하고 결과를 JSON 문자열로 받습니다.
                        String jsonResult = executor.execute(args);

                        // ToolExecutor에서 해당 도구에 특화된 템플릿화된 프롬프트를 가져옵니다.
                        // 이 프롬프트는 함수 실행 결과를 모델에 다시 전달할 때 사용됩니다.
                        String templatedPrompt = executor.getTemplatedPrompt(request.getPrompt());

                        // ToolExecutor에서 해당 도구에 특화된 시스템 지침을 가져와 기존 설정에 적용합니다.
                        // 이는 특정 도구 사용 시 모델의 행동을 미세 조정하는 데 사용될 수 있습니다.
                        GenerateContentConfig tunedConfig = config.toBuilder()
                                .systemInstruction(executor.getSystemInstruction())
                                .build();

                        // 5. 함수 실행 결과와 템플릿화된 프롬프트를 포함하여 Gemini 모델에 두 번째 콘텐츠 생성 요청을 보냅니다.
                        //    이 요청은 모델이 함수 실행 결과를 바탕으로 최종 리포트를 생성하도록 유도합니다.
                        GenerateContentResponse finalResponse = geminiClient.models.generateContent(
                                USED_LLM_MODEL, // 사용할 LLM 모델 지정
                                Content.fromParts(
                                        Part.fromText(templatedPrompt), // 템플릿화된 프롬프트
                                        Part.fromFunctionResponse(functionName, ImmutableMap.of(
                                                "result", jsonResult // 함수 실행 결과 (JSON 문자열)
                                        ))
                                ),
                                tunedConfig); // 조정된 설정 객체
                        return finalResponse.text(); // 최종 응답 텍스트 반환
                    })
                    // FunctionCall이 없거나 처리 중 문제가 발생하여 Optional이 비어있으면,
                    // Gemini 모델의 첫 번째 응답 텍스트를 그대로 반환합니다.
                    .orElseGet(response::text);
        }).doOnError(e -> {
            // 비동기 처리 중 에러 발생 시 에러 메시지를 출력합니다.
            System.err.println("Error during Gemini API call: " + e.getMessage());
        });
    }

    /**
     * {@link GenerateContentResponse} 객체에서 첫 번째 {@link FunctionCall}을 추출하는 헬퍼 메서드입니다.
     * 이 메서드는 응답의 후보(candidates), 콘텐츠(content), 파트(parts)를 순차적으로 탐색하여
     * {@link FunctionCall}이 포함된 첫 번째 파트를 찾습니다.
     * <p>
     * 이 메서드는 {@link Optional} 체인을 사용하여 null 체크 없이 안전하게 데이터를 추출하며,
     * 가독성을 높이고 {@code getReport} 메서드의 복잡성을 줄입니다.
     *
     * @param response Gemini 모델로부터 받은 {@link GenerateContentResponse} 객체.
     * @return 응답에서 찾은 {@link FunctionCall}을 포함하는 {@link Optional} 객체.
     *         {@link FunctionCall}이 없으면 빈 {@link Optional}을 반환합니다.
     */
    private Optional<FunctionCall> findFunctionCall(GenerateContentResponse response) {
        return Optional.ofNullable(response) // 응답 객체가 null이 아닐 경우 Optional로 래핑
                .flatMap(GenerateContentResponse::candidates) // 응답에서 후보 목록(List<Candidate>)을 Optional로 추출
                .filter(candidates -> !candidates.isEmpty()) // 후보 목록이 비어있지 않은 경우만 필터링
                .flatMap(candidates -> candidates.getFirst().content()) // 첫 번째 후보의 콘텐츠(Content)를 Optional로 추출
                .flatMap(Content::parts) // 콘텐츠에서 파트 목록(List<Part>)을 Optional로 추출
                .flatMap(parts -> parts.stream() // 파트 목록을 스트림으로 변환
                        .filter(part -> part.functionCall().isPresent()) // 각 파트에서 FunctionCall이 존재하는 경우만 필터링
                        .findFirst() // FunctionCall이 있는 첫 번째 파트를 Optional로 추출
                )
                .flatMap(Part::functionCall); // 파트에서 FunctionCall 객체를 Optional로 추출
    }
}
