package com.example.gemini_report.controller;

import com.example.gemini_report.dto.CompanyInfoRequest;
import com.example.gemini_report.service.CompanyInfoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/company")
public class CompanyController {

    private final CompanyInfoService companyInfoService;

    public CompanyController(CompanyInfoService companyInfoService) {
        this.companyInfoService = companyInfoService;
    }

    @PostMapping("/update")
    public ResponseEntity<String> updateCompanyInfo(@RequestBody CompanyInfoRequest request) {
        companyInfoService.updateCompanyInfo(request.getCompanyInfo());
        return ResponseEntity.ok("Company information updated successfully.");
    }
}
