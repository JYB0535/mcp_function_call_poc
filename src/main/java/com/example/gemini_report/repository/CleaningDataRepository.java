package com.example.gemini_report.repository;

import com.example.gemini_report.entity.CleaningData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CleaningDataRepository extends JpaRepository<CleaningData, Long> {

    List<CleaningData> findByStartTimeBetween(LocalDateTime start, LocalDateTime end);
}
