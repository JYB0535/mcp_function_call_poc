package com.example.gemini_report.config;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.genai.types.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GeminiConfigFactory {

    private static final ImmutableList<SafetySetting> SAFETY_SETTINGS =
            ImmutableList.of(
                    SafetySetting.builder()
                            .category(HarmCategory.Known.HARM_CATEGORY_HATE_SPEECH)
                            .threshold(HarmBlockThreshold.Known.BLOCK_ONLY_HIGH)
                            .build(),
                    SafetySetting.builder()
                            .category(HarmCategory.Known.HARM_CATEGORY_DANGEROUS_CONTENT)
                            .threshold(HarmBlockThreshold.Known.BLOCK_LOW_AND_ABOVE)
                            .build());

    public GenerateContentConfig createDefaultGenerateContentConfig(List<Tool> tools) {
        GenerateContentConfig.Builder configBuilder = GenerateContentConfig.builder()
//                .responseMimeType("application/json")
//                .responseJsonSchema(JSON_SCHEMA)
                .thinkingConfig(ThinkingConfig.builder().thinkingBudget(0))
                .candidateCount(1)
                .maxOutputTokens(1024)
                .safetySettings(SAFETY_SETTINGS);

        if (tools != null && !tools.isEmpty()) {
            configBuilder.tools(tools);
        }

        return configBuilder.build();
    }

//    private static final Schema JSON_SCHEMA = Schema.builder()
//            .type(Type.Known.OBJECT)
//            .properties(
//                    ImmutableMap.of(
//                            "속성1", Schema.builder()
//                                    .type(Type.Known.STRING)
//                                    .description("설명1")
//                                    .build(),
//                            "속성2", Schema.builder()
//                                    .type(Type.Known.ARRAY)
//                                    .items(Schema.builder()
//                                            .type(Type.Known.STRING)
//                                            .description("설명2-1")
//                                            .build()
//                                    )
//                                    .description("설명2")
//                                    .build(),
//                            "속성3", Schema.builder()
//                                    .type(Type.Known.OBJECT)
//                                    .properties(
//                                            ImmutableMap.of(
//                                                    "속성3-1", Schema.builder()
//                                                            .type(Type.Known.STRING)
//                                                            .description("설명3-1")
//                                                            .build(),
//                                                    "속성3-2", Schema.builder()
//                                                            .type(Type.Known.STRING)
//                                                            .description("설명3-2")
//                                                            .build(),
//                                                    "속성3-3", Schema.builder()
//                                                            .type(Type.Known.STRING)
//                                                            .description("설명3-3")
//                                                            .build()
//                                            )
//                                    )
//                                    .description("설명3")
//                                    .build()
//                    )
//            )
//            .description("설명")
//            .build();
}
