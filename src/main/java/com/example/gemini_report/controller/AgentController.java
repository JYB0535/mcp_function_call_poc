package com.example.gemini_report.controller;

import com.example.gemini_report.dto.AgentRequest;
import com.example.gemini_report.dto.AgentResponse;
import com.example.gemini_report.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @PostMapping("/report")
    public Mono<AgentResponse> getReport(@RequestBody AgentRequest request) {
        return agentService.getReport(request)
                .map(AgentResponse::new);
    }
}
