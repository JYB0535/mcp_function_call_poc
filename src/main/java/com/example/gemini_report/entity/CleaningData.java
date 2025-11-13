package com.example.gemini_report.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
public class CleaningData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long cleaningId;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String location;
    private Long duration; // in minutes
    private Double areaCleaned; // in square meters
    private Double waterUsage; // in liters
    private Double powerUsage; // in kWh
}
