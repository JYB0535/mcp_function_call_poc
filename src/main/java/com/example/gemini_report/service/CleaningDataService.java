package com.example.gemini_report.service;

import com.example.gemini_report.entity.CleaningData;
import com.example.gemini_report.repository.CleaningDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CleaningDataService {

    private static CleaningDataRepository staticRepository;
    private final CleaningDataRepository repository;

    @jakarta.annotation.PostConstruct
    private void init() {
        staticRepository = repository;
    }

    /**
     * This method is designed to be called via reflection by the AgentService.
     * It must be static to be used with the automatic function calling feature.
     */
    public static List<CleaningData> get_cleaning_report(String startDate, String endDate) {
        if (startDate == null || startDate.isEmpty()) {
            startDate = LocalDate.now().minusWeeks(1).toString();
        }
        if (endDate == null || endDate.isEmpty()) {
            endDate = LocalDate.now().toString();
        }

        LocalDateTime startDateTime = LocalDate.parse(startDate).atStartOfDay();
        LocalDateTime endDateTime = LocalDate.parse(endDate).atTime(LocalTime.MAX);

        return staticRepository.findByStartTimeBetween(startDateTime, endDateTime);
    }
}
