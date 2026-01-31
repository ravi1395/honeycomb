package com.example.honeycomb.service;

import com.example.honeycomb.config.HoneycombAutoscaleProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

@Service
public class AutoScaleService {
    private static final Logger log = LoggerFactory.getLogger(AutoScaleService.class);

    private final HoneycombAutoscaleProperties props;
    private final RequestMetricsService metricsService;
    private final CellServerManager serverManager;

    public AutoScaleService(HoneycombAutoscaleProperties props,
                            RequestMetricsService metricsService,
                            CellServerManager serverManager) {
        this.props = props;
        this.metricsService = metricsService;
        this.serverManager = serverManager;
    }

    @Scheduled(fixedDelayString = "${honeycomb.autoscale.evaluation-interval:30s}")
    public void evaluate() {
        if (!props.isEnabled()) return;
        Duration window = props.getEvaluationInterval();
        Map<String, Double> rps = metricsService.snapshotRpsAndReset(window);
        for (Map.Entry<String, Double> e : rps.entrySet()) {
            String cell = e.getKey();
            if (!props.isCellEnabled(cell)) continue;
            double rate = e.getValue();
            boolean running = serverManager.getCellStatus(cell)
                    .map(status -> status.running())
                    .orElse(false);
            if (!running && rate >= props.getScaleUpRps()) {
                boolean started = serverManager.startCellServer(cell);
                log.info("autoscale start cell={} rate={} started={}", cell, rate, started);
            } else if (running && rate <= props.getScaleDownRps()) {
                boolean stopped = serverManager.stopCellServer(cell);
                log.info("autoscale stop cell={} rate={} stopped={}", cell, rate, stopped);
            }
        }
    }
}
