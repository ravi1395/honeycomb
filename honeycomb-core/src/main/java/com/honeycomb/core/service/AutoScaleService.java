package com.honeycomb.core.service;

import com.honeycomb.core.config.HoneycombAutoscaleProperties;
import com.honeycomb.core.util.HoneycombConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

/**
 * Periodically evaluates per-cell request rates and automatically
 * starts or stops cell servers based on configurable RPS thresholds.
 *
 * <p>Runs on a fixed schedule controlled by
 * {@code honeycomb.autoscale.evaluation-interval}. When the
 * windowed RPS exceeds the scale-up threshold a stopped cell is
 * started; when it drops below the scale-down threshold a running
 * cell is stopped.</p>
 *
 * @see com.honeycomb.core.config.HoneycombAutoscaleProperties
 * @see RequestMetricsService
 */
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
