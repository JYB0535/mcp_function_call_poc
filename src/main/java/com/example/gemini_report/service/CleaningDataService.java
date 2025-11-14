package com.example.gemini_report.service;

import com.example.gemini_report.entity.CleaningData; // CleaningData 엔티티 클래스 임포트
import com.example.gemini_report.repository.CleaningDataRepository; // CleaningDataRepository 인터페이스 임포트
import lombok.RequiredArgsConstructor; // Lombok 어노테이션으로 생성자 자동 생성
import org.springframework.stereotype.Service; // Spring 서비스 컴포넌트임을 나타내는 어노테이션

import java.time.LocalDate; // 날짜 정보만 다루는 LocalDate 클래스 임포트
import java.time.LocalDateTime; // 날짜와 시간 정보를 다루는 LocalDateTime 클래스 임포트
import java.time.LocalTime; // 시간 정보만 다루는 LocalTime 클래스 임포트
import java.util.List; // List 인터페이스 임포트

/**
 * {@code CleaningDataService}는 청소 데이터와 관련된 비즈니스 로직을 처리하는 서비스 클래스입니다.
 * 주로 데이터베이스에서 청소 데이터를 조회하는 기능을 제공합니다.
 * <p>
 * 이 클래스는 {@code @Service} 어노테이션을 통해 Spring의 서비스 계층 컴포넌트로 등록되며,
 * {@code @RequiredArgsConstructor}를 통해 final 필드인 {@link CleaningDataRepository}에 대한
 * 생성자 주입을 자동으로 처리합니다.
 * <p>
 * 이 서비스의 메서드는 {@link com.example.gemini_report.tools.ToolExecutor} 구현체에서 호출되어
 * Gemini 모델의 함수 호출(Function Call)에 대한 실제 데이터 조회 작업을 수행합니다.
 */
@Service
@RequiredArgsConstructor
public class CleaningDataService {
    // 청소 데이터에 대한 데이터베이스 접근을 담당하는 JpaRepository 인터페이스.
    // Spring Data JPA에 의해 자동으로 구현체가 생성되어 주입됩니다.
    private final CleaningDataRepository repository;

    /**
     * 지정된 시작일과 종료일 사이의 청소 데이터를 조회합니다.
     * <p>
     * 이 메서드는 {@link com.example.gemini_report.tools.ToolExecutor}에 의해 호출될 수 있도록 설계되었으며,
     * {@code startDate}와 {@code endDate} 인자를 받아 해당 기간 동안의 모든 청소 기록을 반환합니다.
     * <p>
     * 인자로 받은 날짜가 {@code null}이거나 비어있는 경우, 기본값으로
     * {@code startDate}는 현재 날짜로부터 1주일 전, {@code endDate}는 현재 날짜로 설정됩니다.
     *
     * @param startDate 조회할 기간의 시작일 (YYYY-MM-DD 형식의 문자열). {@code null} 또는 빈 문자열일 경우 기본값 사용.
     * @param endDate 조회할 기간의 종료일 (YYYY-MM-DD 형식의 문자열). {@code null} 또는 빈 문자열일 경우 기본값 사용.
     * @return 지정된 기간 내에 발생한 {@link CleaningData} 객체들의 리스트.
     */
    public List<CleaningData> get_cleaning_report(String startDate, String endDate) {
        // 시작일이 null이거나 비어있는 경우, 현재 날짜로부터 1주일 전으로 기본값을 설정합니다.
        if (startDate == null || startDate.isEmpty()) {
            startDate = LocalDate.now().minusWeeks(1).toString();
        }
        // 종료일이 null이거나 비어있는 경우, 현재 날짜로 기본값을 설정합니다.
        if (endDate == null || endDate.isEmpty()) {
            endDate = LocalDate.now().toString();
        }

        // 문자열 형태의 시작일을 LocalDate 객체로 파싱하고, 해당 날짜의 시작 시간(00:00:00)으로 LocalDateTime을 생성합니다.
        LocalDateTime startDateTime = LocalDate.parse(startDate).atStartOfDay();
        // 문자열 형태의 종료일을 LocalDate 객체로 파싱하고, 해당 날짜의 마지막 시간(23:59:59.999999999)으로 LocalDateTime을 생성합니다.
        LocalDateTime endDateTime = LocalDate.parse(endDate).atTime(LocalTime.MAX);

        // CleaningDataRepository를 사용하여 시작 시간과 종료 시간 사이에 있는 모든 청소 데이터를 조회하여 반환합니다.
        return repository.findByStartTimeBetween(startDateTime, endDateTime);
    }
}
