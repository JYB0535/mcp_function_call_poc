package com.example.gemini_report.service;

import com.google.genai.Client;
import com.google.genai.types.EmbedContentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CompanyInfoService {
    private final Client client;
    private final MilvusService milvusService; // MilvusService 주입


    public Map<String, Object> getCompanyInfo(String userQuery) {
        Map<String, Object> result = new HashMap<>();

        if (userQuery == null || userQuery.trim().isEmpty()) {
            result.put("errorMessage", "{\"error\": \"userQuery is missing or empty.\"}");
            return result;
        }

        List<Float> userQueryEmbedding;
        try {
            // 사용자 쿼리를 임베딩합니다.
            EmbedContentResponse userVecResp = this.client.models.embedContent("gemini-embedding-001", userQuery, null);
            float[] userVecArray = extractEmbedding(userVecResp);
            
            // float[]를 List<Float>으로 변환
            userQueryEmbedding = new ArrayList<>();
            for (float f : userVecArray) {
                userQueryEmbedding.add(f);
            }
        } catch (Exception e) {
            result.put("errorMessage", "{\"error\": \"Failed to process user query.\"}");
            return result;
        }

        if (userQueryEmbedding.isEmpty()) {
            result.put("errorMessage", "{\"error\": \"Could not generate embedding for the user query.\"}");
            return result;
        }

        // Milvus에서 사용자 쿼리 임베딩과 가장 유사한 회사 정보를 검색합니다.
        // topK는 가장 유사한 결과를 몇 개 가져올지 지정합니다. 여기서는 1개만 가져옵니다.
        List<Map<String, Object>> searchResults = milvusService.search(userQueryEmbedding, 1);

        String bestMatch = "관련 정보를 찾을 수 없습니다.";
        float bestScore = 0.0f; // 유사도 점수는 0.0으로 초기화

        if (!searchResults.isEmpty()) {
            // 검색 결과가 있다면 가장 유사한 정보를 추출합니다.
            Map<String, Object> topResult = searchResults.get(0);
            bestMatch = (String) topResult.get("original_text"); // MilvusService에서 정의한 필드 이름
            bestScore = (float) topResult.get("score");
        }

        result.put("사용자_질문", userQuery);
        result.put("가장_유사한_정보", bestMatch);
        result.put("유사도_점수", bestScore);
        return result;
    }




    public void updateCompanyInfo(String companyInfo) {
        System.out.println("회사 정보 업데이트 및 Milvus에 삽입 중...");
        // 기존 컬렉션 삭제 후 재생성하여 이전 데이터를 모두 지웁니다.
        milvusService.recreateCollection();

        List<List<Float>> embeddingsToInsert = new ArrayList<>();
        List<String> textsToInsert = new ArrayList<>();

        try {
            // Gemini 모델을 사용하여 회사 정보 텍스트를 임베딩합니다.
            EmbedContentResponse resp = this.client.models.embedContent("gemini-embedding-001", companyInfo, null);
            float[] embeddingArray = extractEmbedding(resp);

            // float[]를 List<Float>으로 변환
            List<Float> embeddingList = new ArrayList<>();
            for (float f : embeddingArray) {
                embeddingList.add(f);
            }
            embeddingsToInsert.add(embeddingList);
            textsToInsert.add(companyInfo);
        } catch (Exception e) {
            System.err.println("임베딩 생성 실패: " + companyInfo);
            e.printStackTrace();
        }

        // 생성된 모든 임베딩과 원본 텍스트를 Milvus에 한 번에 삽입합니다.
        if (!embeddingsToInsert.isEmpty()) {
            milvusService.insert(embeddingsToInsert, textsToInsert);
            System.out.println("회사 정보 임베딩이 Milvus에 성공적으로 삽입되었습니다.");
        } else {
            System.out.println("삽입할 회사 정보 임베딩이 없습니다.");
        }
    }

    private static float[] extractEmbedding(EmbedContentResponse response) {
        return Optional.ofNullable(response)
                .flatMap(EmbedContentResponse::embeddings)
                .flatMap(embeddings -> embeddings.stream().findFirst())
                .flatMap(embedding -> embedding.values().map(list -> {
                    float[] temp = new float[list.size()];
                    for (int i = 0; i < list.size(); i++) {
                        temp[i] = list.get(i);
                    }
                    return temp;
                }))
                .orElse(new float[0]);
    }



}
