package com.example.honeycomb.service;

import com.example.honeycomb.config.HoneycombAutoscaleProperties;
import com.example.honeycomb.util.HoneycombConstants;
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

    @Scheduled(fixedDelayString = HoneycombConstants.ConfigKeys.AUTOSCALE_EVAL_INTERVAL)
    public void evaluate() {
        if (!props.isEnabled()) return;
        Duration window = props.getEvaluationInterval();
        Map<String, Double> rps = metricsService.snapshotRpsAndReset(window);
        for (Map.Entry<String, Double> e : rps.entrySet()) {
            String cell = e.getKey();
            if (!props.isCellEnabled(cell)) continue;
            double rate = e.getValue();
            double scaleUpRps = props.resolveScaleUpRps(cell);
            double scaleDownRps = props.resolveScaleDownRps(cell);
            boolean running = serverManager.getCellStatus(cell)
                    .map(status -> status.running())
                    .orElse(false);
            if (!running && rate >= scaleUpRps) {
                boolean started = serverManager.startCellServer(cell);
                log.info(HoneycombConstants.Messages.AUTO_SCALE_START, cell, rate, started);
            } else if (running && rate <= scaleDownRps) {
                boolean stopped = serverManager.stopCellServer(cell);
                log.info(HoneycombConstants.Messages.AUTO_SCALE_STOP, cell, rate, stopped);
            }
        }
    }
}
