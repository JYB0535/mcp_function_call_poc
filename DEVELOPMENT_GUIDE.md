# 개발 가이드 (Development Guide)

## 1. 서론 (Introduction)

### 1.1. 프로젝트 개요 (Project Overview)
이 프로젝트는 Google Gemini 모델의 함수 호출(Function Calling) 기능을 활용하여 사용자 요청에 따라 동적으로 데이터를 조회하고, 조회된 데이터를 기반으로 리포트를 생성하는 Spring Boot 기반의 백엔드 애플리케이션입니다. Gemini 모델과의 상호작용을 통해 복잡한 질의에 대한 응답을 생성하며, 내부 시스템의 데이터를 활용하여 보다 정확하고 풍부한 정보를 제공하는 것을 목표로 합니다.

### 1.2. 개발 가이드 목적 (Purpose of this Guide)
이 문서는 본 프로젝트를 확장하거나 유지보수할 개발자를 위해 작성되었습니다. 프로젝트의 핵심 아키텍처, 주요 컴포넌트, 동작 플로우, 그리고 적용된 디자인 패턴에 대한 상세한 설명을 제공합니다. 이를 통해 개발자는 코드베이스를 빠르게 이해하고, 새로운 기능을 효율적으로 추가하며, 기존 기능을 안정적으로 개선할 수 있습니다.

## 2. 선수 지식 (Prerequisites)

본 프로젝트 개발에 참여하기 위해서는 다음 기술 스택에 대한 기본적인 이해가 필요합니다.

*   **Java 및 Spring Boot**: 프로젝트의 기반이 되는 언어 및 프레임워크입니다. 의존성 주입(DI), AOP, Spring Data JPA 등의 개념을 이해하고 있어야 합니다.
*   **Reactor (WebFlux)**: 비동기 및 논블로킹 처리를 위해 Spring WebFlux와 Reactor 라이브러리를 사용합니다. `Mono`, `Flux`와 같은 리액티브 프로그래밍 개념에 대한 이해가 필요합니다.
*   **Google Gemini API 및 함수 호출(Function Calling) 개념**: Gemini 모델과의 상호작용 방식, 특히 모델이 외부 함수를 호출하도록 유도하는 '함수 호출' 메커니즘에 대한 이해가 필수적입니다.
*   **Lombok**: 코드의 반복적인 부분을 줄여주는 라이브러리입니다. `@RequiredArgsConstructor`, `@Getter` 등의 어노테이션 사용법을 알아야 합니다.
*   **Guava (Immutable Collections)**: 불변(Immutable) 컬렉션을 생성하는 데 사용됩니다. `ImmutableList`, `ImmutableMap` 등의 사용법을 이해해야 합니다.
*   **Jackson (JSON Processing)**: 자바 객체와 JSON 데이터 간의 변환을 처리하는 데 사용됩니다. `ObjectMapper`의 기본 사용법을 알아야 합니다.

## 3. 시스템 아키텍처 및 주요 컴포넌트 (System Architecture and Key Components)

### 3.1. 전체 시스템 개요 (Overall System Overview)
본 시스템은 사용자 요청을 받아 Gemini 모델에 전달하고, Gemini 모델이 특정 도구(Tool)의 사용을 제안하면 해당 도구를 실행하여 결과를 다시 Gemini 모델에 전달하는 방식으로 동작합니다. 이 과정을 통해 Gemini 모델은 외부 시스템의 데이터를 활용하여 최종적인 응답을 생성합니다.

```
[사용자] --(HTTP 요청)--> [AgentController]
                                |
                                V
                          [AgentService]
                                |
                                V
                        [Gemini 모델 (1차 호출)] --(함수 호출 제안)--> [AgentService]
                                |                                         |
                                V                                         V
                          [ToolRegistry] <--(도구 조회)--> [ToolExecutor (예: CleaningReportToolExecutor)]
                                |                                         |
                                V                                         V
                          [ToolExecutor 실행] --(데이터 조회)--> [CleaningDataService] --(DB 접근)--> [CleaningDataRepository]
                                |
                                V
                          [AgentService] <--(도구 실행 결과)
                                |
                                V
                        [Gemini 모델 (2차 호출)] <--(도구 실행 결과 포함)
                                |
                                V
                          [AgentService] <--(최종 응답)
                                |
                                V
[사용자] <--(HTTP 응답)---- [AgentController]
```

### 3.2. 주요 컴포넌트 설명 (Description of Key Components)

*   **`AgentController` (`src/main/java/com/example/gemini_report/controller/AgentController.java`)**
    *   **역할**: 외부로부터의 HTTP 요청을 받아 {@code AgentService}로 전달하고, {@code AgentService}의 처리 결과를 HTTP 응답으로 반환하는 REST API 엔드포인트입니다.
    *   **특징**: Spring WebFlux를 사용하여 논블로킹 방식으로 요청을 처리합니다.

*   **`AgentService` (`src/main/java/com/example/gemini_report/service/AgentService.java`)**
    *   **역할**: 프로젝트의 핵심 비즈니스 로직을 담당합니다. 사용자 요청을 Gemini 모델에 전달하고, 모델의 응답을 분석하여 함수 호출을 처리하며, 도구 실행 결과를 바탕으로 최종 응답을 생성합니다.
    *   **특징**:
        *   {@link com.example.gemini_report.tools.ToolRegistry}와 {@link com.example.gemini_report.config.GeminiConfigFactory}를 주입받아 사용합니다.
        *   Gemini 모델과의 상호작용 로직과 함수 호출 처리 로직을 캡슐화합니다.
        *   {@code findFunctionCall} 헬퍼 메서드를 통해 {@code Optional} 체인을 활용한 안전하고 가독성 높은 함수 호출 감지 로직을 구현합니다.

*   **`CleaningDataService` (`src/main/java/com/example/gemini_report/service/CleaningDataService.java`)**
    *   **역할**: 청소 데이터와 관련된 비즈니스 로직을 처리하는 서비스입니다. 주로 데이터베이스에서 청소 데이터를 조회하는 기능을 제공합니다.
    *   **특징**: {@link CleaningDataRepository}를 사용하여 실제 데이터베이스 접근을 수행합니다. {@link com.example.gemini_report.tools.CleaningReportToolExecutor}에 의해 호출됩니다.

*   **`CleaningDataRepository` (`src/main/java/com/example/gemini_report/repository/CleaningDataRepository.java`)**
    *   **역할**: 청소 데이터(CleaningData 엔티티)에 대한 데이터베이스 접근을 담당하는 Spring Data JPA 리포지토리 인터페이스입니다.
    *   **특징**: `findByStartTimeBetween`과 같은 메서드를 통해 특정 기간의 데이터를 조회하는 기능을 제공합니다.

*   **`GeminiConfigFactory` (`src/main/java/com/example/gemini_report/config/GeminiConfigFactory.java`)**
    *   **역할**: Gemini 모델의 콘텐츠 생성 설정({@code GenerateContentConfig}) 객체를 생성하는 팩토리 클래스입니다.
    *   **특징**:
        *   공통적인 안전 설정({@code SAFETY_SETTINGS})을 정의합니다.
        *   {@link SystemInstructionProvider}를 주입받아 동적으로 시스템 지침을 가져와 설정에 포함합니다.
        *   {@link ToolRegistry}로부터 받은 도구 목록을 {@code GenerateContentConfig}에 추가합니다.
        *   모델 설정과 관련된 로직을 {@code AgentService}로부터 분리하여 관리합니다.

*   **`SystemInstructionProvider` (`src/main/java/com/example/gemini_report/config/SystemInstructionProvider.java`)**
    *   **역할**: Gemini 모델에 전달할 시스템 지침({@code Content})을 제공하는 인터페이스입니다.
    *   **특징**: {@code getSystemInstruction(List<Tool> tools)} 메서드를 통해 현재 활성화된 도구 목록을 기반으로 동적인 시스템 지침을 생성할 수 있는 확장 지점을 제공합니다.

*   **`DefaultSystemInstructionProvider` (`src/main/java/com/example/gemini_report/config/DefaultSystemInstructionProvider.java`)**
    *   **역할**: {@link SystemInstructionProvider} 인터페이스의 기본 구현체입니다.
    *   **특징**: 현재는 고정된 기본 시스템 지침을 반환하지만, 향후 도구 목록이나 다른 컨텍스트에 따라 시스템 지침을 동적으로 생성하도록 확장될 수 있습니다.

*   **`ToolExecutor` (`src/main/java/com/example/gemini_report/tools/ToolExecutor.java`)**
    *   **역할**: Gemini 모델의 함수 호출에 의해 실행될 수 있는 모든 도구의 계약을 정의하는 인터페이스입니다.
    *   **특징**:
        *   도구의 이름, {@code FunctionDeclaration} (모델에 도구 설명), 실제 실행 로직({@code execute}), 도구 실행 후 모델에 전달할 템플릿화된 프롬프트({@code getTemplatedPrompt}), 그리고 도구에 특화된 시스템 지침({@code getSystemInstruction})을 정의하는 메서드를 포함합니다.
        *   새로운 도구를 추가할 때 이 인터페이스를 구현해야 합니다.

*   **`CleaningReportToolExecutor` (`src/main/java/com/example/gemini_report/tools/CleaningReportToolExecutor.java`)**
    *   **역할**: {@link ToolExecutor} 인터페이스의 구현체로, 청소 데이터 리포트 생성 기능을 담당하는 도구입니다.
    *   **특징**:
        *   "get_cleaning_report"라는 함수 호출 이름을 가집니다.
        *   {@link CleaningDataService}를 사용하여 실제 청소 데이터를 조회하고, {@code ObjectMapper}를 사용하여 결과를 JSON 문자열로 변환합니다.
        *   도구 실행 후 모델에 전달할 특정 템플릿 프롬프트와 시스템 지침을 제공합니다.

*   **`ToolRegistry` (`src/main/java/com/example/gemini_report/tools/ToolRegistry.java`)**
    *   **역할**: 시스템에 등록된 모든 {@link ToolExecutor} 구현체들을 관리하는 중앙 레지스트리입니다.
    *   **특징**:
        *   Spring 컨테이너에 의해 모든 {@link ToolExecutor} 빈들을 자동으로 수집합니다.
        *   도구 이름을 키로 하는 맵 형태로 {@link ToolExecutor} 인스턴스를 저장하여 빠른 조회를 가능하게 합니다.
        *   Gemini 모델에 제공할 {@link Tool} 객체 목록을 생성하여 반환합니다.

*   **`FunctionCallNames` (`src/main/java/com/example/gemini_report/config/FunctionCallNames.java`)**
    *   **역할**: Gemini 모델의 함수 호출 기능에서 사용되는 함수들의 이름을 상수로 정의하는 클래스입니다.
    *   **특징**: 함수 이름의 중앙 집중식 관리를 통해 오타를 방지하고 코드의 일관성을 유지합니다.

## 4. 동작 플로우 (Execution Flow)

사용자 요청이 들어와 Gemini 모델을 통해 최종 응답이 반환되기까지의 주요 동작 플로우는 다음과 같습니다.

1.  **사용자 요청 수신**: {@code AgentController}가 사용자로부터 HTTP 요청(예: "지난주 청소 리포트 보여줘")을 수신합니다.
2.  **`AgentService` 호출**: {@code AgentController}는 요청을 {@code AgentService}의 `getReport` 메서드로 전달합니다.
3.  **도구 목록 및 Gemini 설정 준비**:
    *   {@code AgentService}는 {@link ToolRegistry}를 통해 시스템에 등록된 모든 {@link ToolExecutor}들의 {@code FunctionDeclaration}을 가져와 {@code Tool} 객체 목록을 생성합니다.
    *   {@code AgentService}는 {@link GeminiConfigFactory}를 호출하여 이 도구 목록과 기본 안전 설정 등을 포함하는 {@code GenerateContentConfig} 객체를 생성합니다. 이때 {@link SystemInstructionProvider}를 통해 시스템 지침도 설정됩니다.
4.  **Gemini 모델 1차 호출**: {@code AgentService}는 사용자 프롬프트와 준비된 {@code GenerateContentConfig}를 사용하여 Gemini 모델에 콘텐츠 생성 요청을 보냅니다.
5.  **Gemini 응답 분석 및 함수 호출 감지**:
    *   Gemini 모델은 사용자 프롬프트와 제공된 도구 목록을 분석하여, 특정 도구를 호출해야 한다고 판단하면 응답에 {@code FunctionCall}을 포함하여 반환합니다.
    *   {@code AgentService}의 `findFunctionCall` 헬퍼 메서드는 이 응답에서 {@code FunctionCall}을 추출합니다.
6.  **도구 실행**:
    *   {@code AgentService}는 추출된 {@code FunctionCall}의 함수 이름(예: "get_cleaning_report")을 사용하여 {@link ToolRegistry}에서 해당 {@link ToolExecutor} 구현체(예: {@link CleaningReportToolExecutor})를 조회합니다.
    *   {@code FunctionCall}에 포함된 인자(예: `startDate`, `endDate`)를 추출하여 조회된 {@link ToolExecutor}의 `execute` 메서드를 호출합니다.
    *   {@link CleaningReportToolExecutor}는 {@link CleaningDataService}를 통해 실제 데이터베이스에서 청소 데이터를 조회하고, 그 결과를 JSON 문자열로 변환하여 반환합니다.
7.  **Gemini 모델 2차 호출 (도구 실행 결과 포함)**:
    *   {@code AgentService}는 {@link ToolExecutor}로부터 받은 도구 실행 결과(JSON 문자열)와 {@link ToolExecutor}가 제공하는 템플릿화된 프롬프트({@code getTemplatedPrompt}), 그리고 도구에 특화된 시스템 지침({@code getSystemInstruction})을 포함하여 Gemini 모델에 두 번째 콘텐츠 생성 요청을 보냅니다.
    *   이때, `GenerateContentConfig`는 {@link ToolExecutor}에서 제공하는 시스템 지침으로 조정({@code tunedConfig})되어 사용됩니다.
8.  **최종 응답 반환**: Gemini 모델은 도구 실행 결과를 바탕으로 최종적인 리포트 텍스트를 생성하여 반환하고, {@code AgentService}는 이 텍스트를 {@code AgentController}를 통해 사용자에게 전달합니다.

## 5. 적용된 디자인 패턴 및 사용법 (Applied Design Patterns and Usage)

본 프로젝트는 코드의 확장성, 유지보수성, 가독성을 높이기 위해 여러 디자인 패턴을 적용했습니다.

### 5.1. 팩토리 패턴 (Factory Pattern): `GeminiConfigFactory`
*   **역할**: {@code GeminiConfigFactory}는 Gemini 모델과의 상호작용에 필요한 {@code GenerateContentConfig} 객체를 생성하는 책임을 가집니다. 이 패턴을 통해 {@code AgentService}는 복잡한 설정 객체 생성 로직으로부터 분리되어 핵심 비즈니스 로직에 집중할 수 있습니다.
*   **확장 방법**:
    *   새로운 Gemini 모델 설정 옵션이 필요하거나, 기존 설정의 기본값을 변경해야 할 경우 {@code GeminiConfigFactory} 내부의 `createDefaultGenerateContentConfig` 메서드를 수정합니다.
    *   특정 목적을 위한 다른 종류의 {@code GenerateContentConfig}를 생성해야 한다면, {@code GeminiConfigFactory}에 새로운 팩토리 메서드를 추가할 수 있습니다.

### 5.2. 전략 패턴 (Strategy Pattern): `SystemInstructionProvider`
*   **역할**: {@code SystemInstructionProvider} 인터페이스와 그 구현체({@code DefaultSystemInstructionProvider})는 Gemini 모델에 전달할 시스템 지침을 동적으로 결정하는 전략을 캡슐화합니다. {@code GeminiConfigFactory}는 이 전략을 주입받아 사용합니다.
*   **확장 방법**:
    *   특정 조건(예: 사용자 역할, 요청 유형)에 따라 다른 시스템 지침을 사용하고 싶다면, {@code SystemInstructionProvider} 인터페이스를 구현하는 새로운 클래스를 생성하고, 해당 클래스를 Spring 빈으로 등록합니다.
    *   Spring의 `@Qualifier` 어노테이션 등을 사용하여 {@code GeminiConfigFactory}가 어떤 {@code SystemInstructionProvider} 구현체를 사용할지 선택할 수 있도록 할 수 있습니다.

### 5.3. 커맨드 패턴 (Command Pattern) / 전략 패턴 (Strategy Pattern): `ToolExecutor` 및 구현체
*   **역할**: {@code ToolExecutor} 인터페이스는 Gemini 모델의 함수 호출에 의해 실행될 수 있는 모든 "명령(Command)" 또는 "전략(Strategy)"을 추상화합니다. 각 {@code ToolExecutor} 구현체는 특정 도구의 실행 로직을 캡슐화합니다.
*   **확장 방법**:
    *   새로운 도구를 추가하려면 {@code ToolExecutor} 인터페이스를 구현하는 새로운 클래스를 생성합니다.
    *   이 클래스에서 `getToolName()`, `getFunctionDeclaration()`, `execute()`, `getTemplatedPrompt()`, `getSystemInstruction()` 메서드를 구현하여 도구의 메타데이터와 실행 로직을 정의합니다.
    *   새로운 구현체에 `@Component` 어노테이션을 붙여 Spring 빈으로 등록하면 {@link ToolRegistry}에 의해 자동으로 감지됩니다.

### 5.4. 레지스트리 패턴 (Registry Pattern): `ToolRegistry`
*   **역할**: {@code ToolRegistry}는 시스템에 존재하는 모든 {@link ToolExecutor} 구현체들을 중앙에서 관리하고 조회하는 역할을 합니다. 이는 사용 가능한 도구들을 동적으로 등록하고 런타임에 쉽게 찾아 사용할 수 있도록 합니다.
*   **확장 방법**:
    *   {@code ToolRegistry} 자체를 직접 수정할 필요는 거의 없습니다. 새로운 도구는 {@link ToolExecutor}를 구현하고 Spring 빈으로 등록하는 것만으로 {@code ToolRegistry}에 자동으로 추가됩니다.
    *   만약 도구 조회 방식이나 관리 로직을 변경해야 한다면 {@code ToolRegistry} 내부의 `init()` 또는 `getToolExecutor()` 메서드를 수정할 수 있습니다.

## 6. 확장 가이드 (Extension Guide)

### 6.1. 새로운 도구(Tool) 추가 방법 (How to Add a New Tool)

새로운 기능을 Gemini 모델의 함수 호출을 통해 제공하고 싶다면 다음 단계를 따릅니다.

1.  **함수 호출 이름 상수 정의**:
    *   `src/main/java/com/example/gemini_report/config/FunctionCallNames.java` 파일에 새로운 도구의 고유한 함수 호출 이름을 `public static final String` 상수로 정의합니다. (예: `public static final String GET_WEATHER_REPORT = "get_weather_report";`)

2.  **필요한 서비스/로직 구현**:
    *   새로운 도구가 데이터를 조회하거나 특정 비즈니스 로직을 수행해야 한다면, 해당 로직을 처리할 새로운 서비스 클래스(예: `WeatherService`)를 `src/main/java/com/example/gemini_report/service` 패키지에 생성합니다.

3.  **`ToolExecutor` 구현**:
    *   `src/main/java/com/example/gemini_report/tools` 패키지에 {@link ToolExecutor} 인터페이스를 구현하는 새로운 클래스(예: `WeatherReportToolExecutor`)를 생성합니다.
    *   `@Component` 어노테이션을 붙여 Spring 빈으로 등록합니다.
    *   `getToolName()`: 1단계에서 정의한 함수 호출 이름 상수를 반환합니다.
    *   `getFunctionDeclaration()`: Gemini 모델에 이 도구의 이름, 상세 설명, 필요한 매개변수(이름, 타입, 설명, 필수 여부)를 정의하는 {@code FunctionDeclaration} 객체를 빌드하여 반환합니다.
    *   `execute(Map<String, Object> args)`: 2단계에서 구현한 서비스/로직을 호출하고, Gemini 모델로부터 받은 인자들을 사용하여 실제 기능을 수행합니다. 결과는 JSON 문자열로 변환하여 반환해야 합니다.
    *   `getTemplatedPrompt(String originalPrompt)`: 도구 실행 후 모델에 전달할 템플릿화된 프롬프트를 정의합니다.
    *   `getSystemInstruction()`: 이 도구에 특화된 시스템 지침을 정의합니다.

4.  **의존성 주입 확인**:
    *   새로운 {@code ToolExecutor} 구현체가 필요한 의존성(예: `WeatherService`, `ObjectMapper`)을 생성자를 통해 주입받도록 합니다.

이 단계를 완료하면, {@link ToolRegistry}가 자동으로 새로운 도구를 감지하고 {@code AgentService}가 Gemini 모델과의 상호작용에서 해당 도구를 활용할 수 있게 됩니다.

### 6.3. Gemini 모델 설정 변경 방법 (How to Change Gemini Model Configuration)

Gemini 모델 호출 시 사용되는 {@code GenerateContentConfig}의 세부 설정을 변경하고 싶다면 다음을 따릅니다.

1.  **`GeminiConfigFactory` 수정**:
    *   `src/main/java/com/example/gemini_report/config/GeminiConfigFactory.java` 파일의 `createDefaultGenerateContentConfig` 메서드를 수정합니다.
    *   {@code configBuilder}를 통해 `thinkingConfig`, `candidateCount`, `maxOutputTokens`, `safetySettings` 등의 값을 변경하거나 새로운 설정을 추가할 수 있습니다.
    *   예를 들어, `responseMimeType`이나 `responseSchema`와 같은 설정을 활성화하여 모델이 특정 형식의 JSON 응답을 생성하도록 유도할 수 있습니다.

## 7. 결론 (Conclusion)

이 개발 가이드는 본 프로젝트의 핵심 구조와 동작 원리를 이해하고, 향후 개발자가 효율적으로 기능을 확장하고 유지보수할 수 있도록 돕기 위해 작성되었습니다. 제시된 디자인 패턴과 확장 가이드를 활용하여 안정적이고 유연한 시스템을 구축해 나가시길 바랍니다.
