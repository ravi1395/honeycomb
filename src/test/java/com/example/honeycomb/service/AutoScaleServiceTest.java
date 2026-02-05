package com.example.honeycomb.service;

import com.example.honeycomb.config.HoneycombAutoscaleProperties;
import com.example.honeycomb.dto.CellRuntimeStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoScaleServiceTest {

    @Mock
    private RequestMetricsService metricsService;

    @Mock
    private CellServerManager serverManager;

    @Test
    void startsCellWhenRateAbovePerCellThreshold() {
        HoneycombAutoscaleProperties props = new HoneycombAutoscaleProperties();
        props.setEnabled(true);
        props.setEvaluationInterval(Duration.ofSeconds(15));
        props.setScaleUpRps(5.0);
        props.setScaleDownRps(0.5);
        props.setPerCellScaleUpRps(Map.of("InventoryCell", 1.0));

        when(metricsService.snapshotRpsAndReset(any())).thenReturn(Map.of("InventoryCell", 1.2));
        when(serverManager.getCellStatus("InventoryCell"))
                .thenReturn(Optional.of(new CellRuntimeStatus("InventoryCell", 9091, null, null, false, false, null)));
        when(serverManager.startCellServer("InventoryCell")).thenReturn(true);

        AutoScaleService service = new AutoScaleService(props, metricsService, serverManager);
        service.evaluate();

        verify(serverManager).startCellServer("InventoryCell");
        verify(serverManager, never()).stopCellServer("InventoryCell");
    }

    @Test
    void stopsCellWhenRateBelowPerCellThreshold() {
        HoneycombAutoscaleProperties props = new HoneycombAutoscaleProperties();
        props.setEnabled(true);
        props.setEvaluationInterval(Duration.ofSeconds(15));
        props.setScaleUpRps(5.0);
        props.setScaleDownRps(0.5);
        props.setPerCellScaleDownRps(Map.of("PricingCell", 0.05));

        when(metricsService.snapshotRpsAndReset(any())).thenReturn(Map.of("PricingCell", 0.03));
        when(serverManager.getCellStatus("PricingCell"))
                .thenReturn(Optional.of(new CellRuntimeStatus("PricingCell", 9093, 9093, null, true, false, null)));
        when(serverManager.stopCellServer("PricingCell")).thenReturn(true);

        AutoScaleService service = new AutoScaleService(props, metricsService, serverManager);
        service.evaluate();

        verify(serverManager).stopCellServer("PricingCell");
        verify(serverManager, never()).startCellServer("PricingCell");
    }
}
