package com.example.gemini_report;

import com.example.gemini_report.service.CompanyInfoService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class GeminiReportApplication {

    public static void main(String[] args) {
        SpringApplication.run(GeminiReportApplication.class, args);
    }
}
