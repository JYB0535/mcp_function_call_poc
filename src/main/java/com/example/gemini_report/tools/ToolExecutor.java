package com.example.gemini_report.tools;

import com.google.genai.types.Content; // Gemini 모델의 콘텐츠(텍스트, 이미지, 시스템 지침 등)를 나타내는 클래스 임포트
import com.google.genai.types.FunctionDeclaration; // Gemini 모델에 함수를 선언하기 위한 클래스 임포트
import java.util.Map; // Map 인터페이스 임포트

/**
 * {@code ToolExecutor} 인터페이스는 Gemini 모델의 함수 호출(Function Calling) 기능을 통해
 * 실행될 수 있는 모든 도구(Tool)의 계약을 정의합니다.
 * <p>
 * 이 인터페이스를 구현하는 클래스는 특정 도구의 비즈니스 로직을 캡슐화하며,
 * Gemini 모델이 해당 도구를 호출할 수 있도록 필요한 메타데이터(함수 선언)를 제공합니다.
 * 또한, 도구 실행 후 모델에 전달할 템플릿화된 프롬프트와 시스템 지침을 제공하여
 * 모델의 응답 생성 과정을 유연하게 제어할 수 있도록 합니다.
 * <p>
 * 새로운 도구를 시스템에 추가하려면 이 인터페이스를 구현하는 새로운 클래스를 생성하고,
 * {@link ToolRegistry}에 의해 자동으로 감지되도록 Spring 컴포넌트로 등록해야 합니다.
 */
public interface ToolExecutor {
    /**
     * 이 도구의 고유한 이름을 반환합니다.
     * 이 이름은 Gemini 모델이 함수 호출을 제안할 때 사용하는 이름과 일치해야 합니다.
     *
     * @return 도구의 이름 (문자열).
     */
    String getToolName();

    /**
     * 이 도구에 대한 {@link FunctionDeclaration} 객체를 반환합니다.
     * {@link FunctionDeclaration}은 Gemini 모델에게 이 도구의 이름, 설명,
     * 그리고 필요한 매개변수(타입, 설명, 필수 여부 등)를 알려줍니다.
     *
     * @return 이 도구의 {@link FunctionDeclaration} 객체.
     */
    FunctionDeclaration getFunctionDeclaration();

    /**
     * Gemini 모델로부터 받은 함수 호출 인자들을 사용하여 실제 도구의 비즈니스 로직을 실행합니다.
     * 실행 결과는 Gemini 모델이 이해할 수 있는 JSON 문자열 형태로 반환되어야 합니다.
     *
     * @param args Gemini 모델이 함수 호출 시 제공한 인자들의 맵. 키는 매개변수 이름, 값은 매개변수 값입니다.
     * @return 도구 실행 결과의 JSON 문자열 표현.
     */
    String execute(Map<String, Object> args);

    /**
     * 도구 실행 후 Gemini 모델에 다시 전달할 템플릿화된 프롬프트를 생성하여 반환합니다.
     * 이 프롬프트는 도구 실행 결과와 함께 모델에 전달되어 최종 응답을 생성하는 데 도움을 줍니다.
     * 각 도구는 자신의 목적에 맞는 프롬프트 템플릿을 제공할 수 있습니다.
     *
     * @param originalPrompt 사용자가 처음에 Gemini 모델에 보낸 원본 프롬프트.
     * @return 도구 실행 결과를 포함하여 모델에 전달할 템플릿화된 프롬프트 문자열.
     */
    String getTemplatedPrompt(String originalPrompt);

    /**
     * 이 도구에 특화된 시스템 지침(System Instruction)을 반환합니다.
     * 시스템 지침은 Gemini 모델의 행동을 특정 도구의 컨텍스트에 맞게 조정하는 데 사용될 수 있습니다.
     * 예를 들어, 특정 도구를 사용할 때 모델이 특정 역할을 수행하도록 지시할 수 있습니다.
     *
     * @return 이 도구에 대한 {@link Content} 형태의 시스템 지침.
     */
    Content getSystemInstruction();
}
