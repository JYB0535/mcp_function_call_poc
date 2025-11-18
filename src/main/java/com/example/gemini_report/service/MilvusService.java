package com.example.gemini_report.service;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.*;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.*;
import io.milvus.param.MetricType;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.param.IndexType;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.highlevel.dml.response.SearchResponse;
import io.milvus.response.SearchResultsWrapper;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Milvus 벡터 데이터베이스와의 상호작용을 관리하는 서비스 클래스.
 * 컬렉션 생성, 데이터 삽입, 벡터 검색 등의 기능을 제공합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MilvusService {

    private final MilvusServiceClient milvusServiceClient;

    @Value("${milvus.collection.name}")
    private String COLLECTION_NAME;
    @Value("${milvus.field.id}")
    private String FIELD_NAME_ID;
    @Value("${milvus.field.vector}")
    private String FIELD_NAME_VECTOR;
    @Value("${milvus.field.text}")
    private String FIELD_NAME_TEXT;
    @Value("${milvus.embedding.dimension}")
    private int EMBEDDING_DIMENSION;
    @Value("${milvus.text.max-length}")
    private int MAX_TEXT_LENGTH;
    @Value("${milvus.collection.shards-num}")
    private int SHARDS_NUM;
    @Value("${milvus.search.metric-type}")
    private String METRIC_TYPE;
    @Value("${milvus.search.params}")
    private String SEARCH_PARAMS;

    /**
     * Milvus 컬렉션이 존재하는지 확인하고, 없으면 생성합니다.
     * 컬렉션 스키마는 ID, 임베딩 벡터, 원본 텍스트 필드를 포함합니다.
     */
    @PostConstruct
    public void createCollectionIfNotExists() {
        log.info("Milvus 컬렉션 '{}' 존재 여부 확인 및 생성...", COLLECTION_NAME);
        try {
            R<Boolean> hasCollectionResp = milvusServiceClient.hasCollection(
                    HasCollectionParam.newBuilder()
                            .withCollectionName(COLLECTION_NAME)
                            .build()
            );

            if (hasCollectionResp.getStatus() != R.Status.Success.getCode()) {
                log.error("컬렉션 '{}' 존재 여부 확인 실패: {}", COLLECTION_NAME, hasCollectionResp.getMessage());
                throw new RuntimeException("Milvus 컬렉션 존재 여부 확인 실패: " + hasCollectionResp.getMessage());
            }

            if (hasCollectionResp.getData() == null || !hasCollectionResp.getData()) {
                log.info("컬렉션 '{}'이(가) 존재하지 않습니다. 새로 생성합니다.", COLLECTION_NAME);

                // ID 필드 정의 (Primary Key, Auto ID)
                FieldType idField = FieldType.newBuilder()
                        .withName(FIELD_NAME_ID)
                        .withDataType(DataType.Int64)
                        .withPrimaryKey(true)
                        .withAutoID(true)
                        .build();

                // 임베딩 벡터 필드 정의
                FieldType vectorField = FieldType.newBuilder()
                        .withName(FIELD_NAME_VECTOR)
                        .withDataType(DataType.FloatVector)
                        .withDimension(EMBEDDING_DIMENSION)
                        .build();

                // 원본 텍스트 필드 정의
                FieldType textField = FieldType.newBuilder()
                        .withName(FIELD_NAME_TEXT)
                        .withDataType(DataType.VarChar)
                        .withMaxLength(MAX_TEXT_LENGTH) // 텍스트 길이 제한
                        .build();

                // 컬렉션 생성 파라미터 설정
                CreateCollectionParam createCollectionParam = CreateCollectionParam.newBuilder()
                        .withCollectionName(COLLECTION_NAME)
                        .withDescription("회사 정보 임베딩을 저장하는 컬렉션")
                        .withShardsNum(SHARDS_NUM) // 샤드 수 설정
                        .addFieldType(idField)
                        .addFieldType(vectorField)
                        .addFieldType(textField)
                        .build();

                // 컬렉션 생성
                R<RpcStatus> createCollectionResp = milvusServiceClient.createCollection(createCollectionParam);
                if (createCollectionResp.getStatus() == R.Status.Success.getCode()) {
                    log.info("컬렉션 '{}' 생성 성공.", COLLECTION_NAME);

                    // 벡터 필드에 대한 인덱스 생성
                    log.info("벡터 필드 '{}'에 대한 인덱스 생성 중...", FIELD_NAME_VECTOR);
                    CreateIndexParam createIndexParam = CreateIndexParam.newBuilder()
                            .withCollectionName(COLLECTION_NAME)
                            .withFieldName(FIELD_NAME_VECTOR)
                            .withIndexType(IndexType.IVF_FLAT)
                            .withMetricType(MetricType.valueOf(METRIC_TYPE))
                            .withExtraParam("{\"nlist\":1024}")
                            .build();
                    
                    R<RpcStatus> createIndexResp = milvusServiceClient.createIndex(createIndexParam);
                    if (createIndexResp.getStatus() != R.Status.Success.getCode()) {
                        log.error("인덱스 생성 실패: {}", createIndexResp.getMessage());
                        throw new RuntimeException("Milvus 인덱스 생성 실패: " + createIndexResp.getMessage());
                    }
                    log.info("인덱스 생성 성공.");

                    // 생성 후 즉시 로드
                    loadCollection();
                } else {
                    log.error("컬렉션 '{}' 생성 실패: {}", COLLECTION_NAME, createCollectionResp.getMessage());
                    throw new RuntimeException("Milvus 컬렉션 생성 실패: " + createCollectionResp.getMessage());
                }
            } else {
                log.info("컬렉션 '{}'이(가) 이미 존재합니다.", COLLECTION_NAME);
                // 기존 컬렉션 로드
                loadCollection();
            }
        } catch (Exception e) {
            log.error("Milvus 컬렉션 '{}' 생성 또는 확인 중 예외 발생: {}", COLLECTION_NAME, e.getMessage(), e);
            throw new RuntimeException("Milvus 컬렉션 작업 중 오류 발생: " + e.getMessage(), e);
        }
    }

    /**
     * 컬렉션을 메모리에 로드하여 검색을 가능하게 합니다.
     */
    private void loadCollection() {
        log.info("컬렉션 '{}'을(를) 메모리에 로드 중...", COLLECTION_NAME);
        try {
            R<RpcStatus> loadCollectionResp = milvusServiceClient.loadCollection(
                    LoadCollectionParam.newBuilder()
                            .withCollectionName(COLLECTION_NAME)
                            .build()
            );
            if (loadCollectionResp.getStatus() == R.Status.Success.getCode()) {
                log.info("컬렉션 '{}' 로드 성공.", COLLECTION_NAME);
            } else {
                log.error("컬렉션 '{}' 로드 실패: {}", COLLECTION_NAME, loadCollectionResp.getMessage());
                throw new RuntimeException("Milvus 컬렉션 로드 실패: " + loadCollectionResp.getMessage());
            }
        } catch (Exception e) {
            log.error("Milvus 컬렉션 '{}' 로드 중 예외 발생: {}", COLLECTION_NAME, e.getMessage(), e);
            throw new RuntimeException("Milvus 컬렉션 로드 중 오류 발생: " + e.getMessage(), e);
        }
    }

    /**
     * 컬렉션을 메모리에서 해제합니다.
     */
    private void releaseCollection() {
        log.info("컬렉션 '{}'을(를) 메모리에서 해제 중...", COLLECTION_NAME);
        try {
            R<RpcStatus> releaseCollectionResp = milvusServiceClient.releaseCollection(
                    ReleaseCollectionParam.newBuilder()
                            .withCollectionName(COLLECTION_NAME)
                            .build()
            );
            if (releaseCollectionResp.getStatus() == R.Status.Success.getCode()) {
                log.info("컬렉션 '{}' 해제 성공.", COLLECTION_NAME);
            } else {
                log.error("컬렉션 '{}' 해제 실패: {}", COLLECTION_NAME, releaseCollectionResp.getMessage());
                // 컬렉션 해제 실패는 애플리케이션 동작에 치명적이지 않을 수 있으므로 RuntimeException을 던지지 않고 로깅만 합니다.
            }
        } catch (Exception e) {
            log.error("Milvus 컬렉션 '{}' 해제 중 예외 발생: {}", COLLECTION_NAME, e.getMessage(), e);
            // 컬렉션 해제 실패는 애플리케이션 동작에 치명적이지 않을 수 있으므로 RuntimeException을 던지지 않고 로깅만 합니다.
        }
    }

    /**
     * 임베딩 벡터와 원본 텍스트를 Milvus 컬렉션에 삽입합니다.
     *
     * @param embeddings 삽입할 임베딩 벡터 리스트
     * @param texts      해당 임베딩 벡터에 매핑되는 원본 텍스트 리스트
     */
    public void insert(List<List<Float>> embeddings, List<String> texts) {
        log.info("Milvus 컬렉션 '{}'에 데이터 삽입 중...", COLLECTION_NAME);
        try {
            // 각 필드에 대한 데이터 리스트 생성
            List<InsertParam.Field> fields = new ArrayList<>();
            fields.add(new InsertParam.Field(FIELD_NAME_VECTOR, embeddings)); // 임베딩 벡터 리스트
            fields.add(new InsertParam.Field(FIELD_NAME_TEXT, texts));      // 원본 텍스트 리스트

            // 삽입 파라미터 설정
            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withFields(fields) // 데이터 행 추가
                    .build();

            // 데이터 삽입
            R<io.milvus.grpc.MutationResult> insertResp = milvusServiceClient.insert(insertParam);
            if (insertResp.getStatus() == R.Status.Success.getCode()) {
                log.info("Milvus 컬렉션 '{}'에 {}개 데이터 삽입 성공.", COLLECTION_NAME, embeddings.size());
            } else {
                log.error("Milvus 컬렉션 '{}'에 데이터 삽입 실패: {}", COLLECTION_NAME, insertResp.getMessage());
                throw new RuntimeException("Milvus 데이터 삽입 실패: " + insertResp.getMessage());
            }
        } catch (Exception e) {
            log.error("Milvus 컬렉션 '{}'에 데이터 삽입 중 예외 발생: {}", COLLECTION_NAME, e.getMessage(), e);
            throw new RuntimeException("Milvus 데이터 삽입 중 오류 발생: " + e.getMessage(), e);
        }
    }

    /**
     * 주어진 쿼리 벡터와 가장 유사한 벡터를 Milvus 컬렉션에서 검색합니다.
     *
     * @param queryVector 검색할 쿼리 임베딩 벡터
     * @param topK        가장 유사한 상위 K개의 결과를 반환
     * @return 검색 결과 리스트 (원본 텍스트와 유사도 점수 포함)
     */
    public List<Map<String, Object>> search(List<Float> queryVector, int topK) {
        log.info("Milvus 컬렉션 '{}'에서 유사 벡터 검색 중...", COLLECTION_NAME);
        try {
            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withVectorFieldName(FIELD_NAME_VECTOR) // 검색할 벡터 필드 이름 지정
                    .withVectors(Collections.singletonList(queryVector))
                    .withOutFields(Collections.singletonList(FIELD_NAME_TEXT))  // 원본 텍스트 필드명
                    .withTopK(topK)
                    .withMetricType(MetricType.valueOf(METRIC_TYPE))
                    .withParams(SEARCH_PARAMS)
                    .build();

            // 검색 실행
            R<SearchResults> searchResp = milvusServiceClient.search(searchParam);
            if (searchResp.getStatus() != R.Status.Success.getCode()) {
                log.error("Milvus 검색 실패: {}", searchResp.getMessage());
                throw new RuntimeException("Milvus 검색 실패: " + searchResp.getMessage());
            }

            SearchResults searchResponse = searchResp.getData();
            SearchResultData queryResult = searchResponse.getResults();

            List<Map<String, Object>> results = new ArrayList<>();
            List<Long> idList = queryResult.getIds().getIntId().getDataList();
            List<Float> scoreList = queryResult.getScoresList();
            List<FieldData> fieldDataList = queryResult.getFieldsDataList();

            // 원본 텍스트는 fieldData에서 추출 (필드와 타입에 맞게 직접 파싱 필요)
            List<String> texts = extractTextFromFieldData(fieldDataList, FIELD_NAME_TEXT);

            for (int i = 0; i < idList.size(); i++) {
                Map<String, Object> record = new HashMap<>();
                record.put("id", idList.get(i));
                record.put("score", scoreList.get(i));
                if (texts != null && i < texts.size()) {
                    record.put(FIELD_NAME_TEXT, texts.get(i));
                }
                results.add(record);
            }

            log.info("Milvus 검색 완료. {}개의 결과 반환.", results.size());
            return results;

        } catch (Exception e) {
            log.error("Milvus 컬렉션 '{}'에서 유사 벡터 검색 중 예외 발생: {}", COLLECTION_NAME, e.getMessage(), e);
            throw new RuntimeException("Milvus 검색 중 오류 발생: " + e.getMessage(), e);
        }
    }

    // FieldData에서 텍스트 필드만 선별 추출하는 예시 (필요에 따라 구현 조정)
    private List<String> extractTextFromFieldData(List<FieldData> fieldDataList, String targetFieldName) {
        for (FieldData fieldData : fieldDataList) {
            if (fieldData.getFieldName().equals(targetFieldName)) {
                // Scalars 타입으로 문자열 데이터 추출
                if (fieldData.hasScalars() && fieldData.getScalars().hasStringData()) {
                    return fieldData.getScalars().getStringData().getDataList();
                }
            }
        }
        return null;
    }




    /**
     * Milvus 컬렉션을 삭제합니다. (주의: 모든 데이터가 삭제됩니다.)
     */
    public void dropCollection() {
        log.warn("Milvus 컬렉션 '{}'을(를) 삭제합니다. 이 작업은 되돌릴 수 없습니다!", COLLECTION_NAME);
        try {
            R<RpcStatus> dropCollectionResp = milvusServiceClient.dropCollection(
                    DropCollectionParam.newBuilder()
                            .withCollectionName(COLLECTION_NAME)
                            .build()
            );
            if (dropCollectionResp.getStatus() == R.Status.Success.getCode()) {
                log.info("컬렉션 '{}' 삭제 성공.", COLLECTION_NAME);
            } else {
                log.error("컬렉션 '{}' 삭제 실패: {}", COLLECTION_NAME, dropCollectionResp.getMessage());
                throw new RuntimeException("Milvus 컬렉션 삭제 실패: " + dropCollectionResp.getMessage());
            }
        } catch (Exception e) {
            log.error("Milvus 컬렉션 '{}' 삭제 중 예외 발생: {}", COLLECTION_NAME, e.getMessage(), e);
            throw new RuntimeException("Milvus 컬렉션 삭제 중 오류 발생: " + e.getMessage(), e);
        }
    }

    /**
     * Milvus 컬렉션을 삭제하고 새로 생성합니다.
     * 이 메서드는 기존 데이터를 모두 지우고 새로운 데이터로 채울 때 사용됩니다.
     */
    public void recreateCollection() {
        log.info("Milvus 컬렉션 '{}'을(를) 재생성합니다. 기존 데이터는 모두 삭제됩니다.", COLLECTION_NAME);
        try {
            // 기존 컬렉션 삭제
            R<Boolean> hasCollectionResp = milvusServiceClient.hasCollection(
                    HasCollectionParam.newBuilder()
                            .withCollectionName(COLLECTION_NAME)
                            .build()
            );

            if (hasCollectionResp.getData() != null && hasCollectionResp.getData()) {
                dropCollection();
            }

            // 새 컬렉션 생성
            createCollectionIfNotExists();
            log.info("Milvus 컬렉션 '{}' 재생성 완료.", COLLECTION_NAME);
        } catch (Exception e) {
            log.error("Milvus 컬렉션 '{}' 재생성 중 예외 발생: {}", COLLECTION_NAME, e.getMessage(), e);
            throw new RuntimeException("Milvus 컬렉션 재생성 중 오류 발생: " + e.getMessage(), e);
        }
    }
}