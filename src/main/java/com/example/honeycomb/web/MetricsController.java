package com.example.honeycomb.web;

import com.example.honeycomb.service.RequestMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/honeycomb/metrics")
@Tag(name = "Metrics", description = "Per-cell metrics overview")
public class MetricsController {
    private final RequestMetricsService metricsService;

    public MetricsController(RequestMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @Operation(summary = "Per-cell request counts since start")
    @GetMapping("/cells")
    public Map<String, Long> cellCounts() {
        return metricsService.snapshotCounts();
    }
}
