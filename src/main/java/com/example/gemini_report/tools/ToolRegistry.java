package com.example.gemini_report.tools;

import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Tool; // Gemini 모델에 전달할 도구(Tool) 객체 임포트
import jakarta.annotation.PostConstruct; // Spring의 초기화 콜백 어노테이션 임포트
import lombok.Getter; // Lombok 어노테이션으로 Getter 자동 생성
import lombok.RequiredArgsConstructor; // Lombok 어노테이션으로 생성자 자동 생성
import org.springframework.stereotype.Component; // Spring 컴포넌트임을 나타내는 어노테이션

import java.util.List; // List 인터페이스 임포트
import java.util.Map; // Map 인터페이스 임포트
import java.util.function.Function; // 함수형 인터페이스 임포트
import java.util.stream.Collectors; // 스트림 API의 컬렉터 임포트

/**
 * {@code ToolRegistry}는 시스템에 등록된 모든 {@link ToolExecutor} 구현체들을 관리하는 중앙 레지스트리입니다.
 * 이 클래스는 Spring 애플리케이션 컨텍스트에 의해 자동으로 감지되고,
 * 시스템 내의 모든 {@link ToolExecutor} 빈들을 수집하여 관리합니다.
 * <p>
 * 주요 기능:
 * <ul>
 *     <li>등록된 모든 {@link ToolExecutor}들을 이름(getToolName())을 키로 하는 맵 형태로 저장하여 빠른 조회를 가능하게 합니다.</li>
 *     <li>Gemini 모델에 제공할 {@link Tool} 객체 목록을 생성하여 반환합니다.</li>
 *     <li>특정 도구 이름에 해당하는 {@link ToolExecutor} 인스턴스를 조회하는 기능을 제공합니다.</li>
 * </ul>
 * <p>
 * 새로운 도구를 시스템에 추가하려면 {@link ToolExecutor} 인터페이스를 구현하고
 * {@code @Component} 어노테이션을 붙여 Spring 빈으로 등록하기만 하면,
 * {@code ToolRegistry}가 자동으로 해당 도구를 감지하고 관리 목록에 추가합니다.
 */
@Component
@RequiredArgsConstructor // final 필드인 toolExecutors에 대한 생성자를 자동으로 생성합니다.
public class ToolRegistry {

    // Spring 컨테이너에 의해 주입되는 모든 ToolExecutor 구현체들의 리스트.
    // 애플리케이션 시작 시 Spring이 ToolExecutor 인터페이스를 구현한 모든 빈들을 찾아 이 리스트에 주입합니다.
    private final List<ToolExecutor> toolExecutors;

    // 도구의 이름(String)을 키로 하고 해당 ToolExecutor 인스턴스를 값으로 하는 맵.
    // 빠른 조회를 위해 사용되며, @PostConstruct 메서드에서 초기화됩니다.
    @Getter // Lombok 어노테이션으로 toolExecutorMap에 대한 public getter 메서드를 자동으로 생성합니다.
    private Map<String, ToolExecutor> toolExecutorMap;

    /**
     * Spring 빈 초기화 시 호출되는 메서드입니다.
     * {@link #toolExecutors} 리스트에 있는 모든 {@link ToolExecutor}들을
     * {@link #toolExecutorMap}에 도구 이름을 키로 하여 저장합니다.
     * <p>
     * 이 과정을 통해 {@code ToolRegistry}는 시스템에 등록된 모든 도구들을
     * 이름으로 쉽게 찾아 사용할 수 있는 상태가 됩니다.
     */
    @PostConstruct
    public void init() {
        toolExecutorMap = toolExecutors.stream()
                .collect(Collectors.toMap(ToolExecutor::getToolName, Function.identity()));
    }

    /**
     * 주어진 도구 이름에 해당하는 {@link ToolExecutor} 인스턴스를 반환합니다.
     *
     * @param toolName 조회할 도구의 이름.
     * @return 해당 도구 이름에 매핑되는 {@link ToolExecutor} 인스턴스.
     *         만약 해당 이름의 도구가 등록되어 있지 않다면 {@code null}을 반환합니다.
     */
    public ToolExecutor getToolExecutor(String toolName) {
        return toolExecutorMap.get(toolName);
    }

    /**
     * Gemini 모델에 제공할 수 있는 모든 도구들의 {@link Tool} 객체 리스트를 반환합니다.
     * 각 {@link ToolExecutor}의 {@link FunctionDeclaration}을 사용하여 {@link Tool} 객체를 생성합니다.
     *
     * @return 시스템에 등록된 모든 도구들의 {@link Tool} 객체 리스트.
     */
    public List<Tool> getAllTools() {
        return toolExecutors.stream()
                // 각 ToolExecutor로부터 FunctionDeclaration을 가져와 Tool 객체를 빌드합니다.
                // Gemini 모델은 FunctionDeclaration을 통해 어떤 함수를 호출할 수 있는지 알게 됩니다.
                .map(toolExecutor -> Tool.builder().functionDeclarations(toolExecutor.getFunctionDeclaration()).build())
                .collect(Collectors.toList());
    }
}
