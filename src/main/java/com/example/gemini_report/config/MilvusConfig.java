package com.example.gemini_report.config;

import com.example.gemini_report.service.MilvusService;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PreDestroy;

/**
 * Milvus 클라이언트 설정을 담당하는 Configuration 클래스.
 * application.properties에서 Milvus 연결 정보를 읽어 MilvusServiceClient 빈을 생성하고 관리합니다.
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class MilvusConfig {

    @Value("${milvus.host}") // application.properties에서 Milvus 호스트 주입
    private String milvusHost;

    @Value("${milvus.port}") // application.properties에서 Milvus 포트 주입
    private int milvusPort;

    private MilvusServiceClient milvusServiceClient; // Milvus 클라이언트 객체

    /**
     * MilvusServiceClient 빈을 생성하고 반환합니다.
     * 이 메서드는 Spring 컨테이너에 의해 한 번만 호출되어 Milvus 클라이언트 인스턴스를 생성합니다.
     *
     * @return MilvusServiceClient 인스턴스
     */
    @Bean
    public MilvusServiceClient milvusServiceClient() {
        log.info("MilvusServiceClient 빈 생성 중...");
        milvusServiceClient = new MilvusServiceClient(
                ConnectParam.newBuilder()
                        .withHost(milvusHost)
                        .withPort(milvusPort)
                        .build()
        );
        log.info("Milvus 클라이언트 연결 완료: {}:{}", milvusHost, milvusPort);
        return milvusServiceClient;
    }

    /**
     * 애플리케이션 종료 시 Milvus 클라이언트 연결을 해제합니다.
     * Spring 컨테이너가 이 빈을 파괴할 때 호출됩니다.
     */
    @PreDestroy
    public void destroy() {
        log.info("MilvusServiceClient 빈 소멸 중...");
        if (milvusServiceClient != null) {
            milvusServiceClient.close(); // 클라이언트 연결 해제
            log.info("Milvus 클라이언트 연결 해제 완료.");
        }
    }
}
