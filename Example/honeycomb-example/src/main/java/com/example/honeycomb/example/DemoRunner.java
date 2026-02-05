package com.example.honeycomb.example;

import com.example.honeycomb.util.HoneycombConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class DemoRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoRunner.class);

    private final WebClient webClient;
    private final ObjectProvider<SharedwallClientExample> sharedwallClientExample;

    @Value(ExampleConstants.PropertyValues.SERVER_PORT)
    private int serverPort;

    @Value(ExampleConstants.PropertyValues.API_KEY_HEADER)
    private String apiKeyHeader;

    @Value(ExampleConstants.PropertyValues.ADMIN_KEY)
    private String adminKey;

    public DemoRunner(WebClient.Builder builder, ObjectProvider<SharedwallClientExample> sharedwallClientExample) {
        this.webClient = builder.build();
        this.sharedwallClientExample = sharedwallClientExample;
    }

    @Override
    public void run(ApplicationArguments args) {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(2000);
                runDemo().block();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                log.warn(ExampleConstants.Messages.DEMO_RUNNER_FAILED, ex);
            }
        });
    }

    private Mono<Void> runDemo() {
        String base = HoneycombConstants.Schemes.HTTP + ExampleConstants.Hosts.LOCALHOST + ":" + serverPort;
        return listModels(base)
            .then(describeModel(base, ExampleConstants.Cells.INVENTORY))
                .then(createInventoryItem(base))
                .then(listInventory(base))
                .then(Mono.defer(() -> {
                    SharedwallClientExample client = sharedwallClientExample.getIfAvailable();
                    if (client == null) return Mono.empty();
                    return client.callDiscount(base).then(client.callDiscountViaCells(base));
                }))
                .then(fetchMetrics(base))
                .then(fetchAudit(base))
                .then();
    }

    private Mono<Void> listModels(String base) {
        return webClient.get().uri(base + HoneycombConstants.Paths.HONEYCOMB_MODELS)
                .header(apiKeyHeader, adminKey)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .doOnNext(body -> log.info(ExampleConstants.Messages.LOG_MODELS, body))
                .then();
    }

    private Mono<Void> describeModel(String base, String name) {
        return webClient.get().uri(base + HoneycombConstants.Paths.HONEYCOMB_MODELS + "/" + name)
                .header(apiKeyHeader, adminKey)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .doOnNext(body -> log.info(ExampleConstants.Messages.LOG_DESCRIBE, name, body))
                .then();
    }

    private Mono<Void> createInventoryItem(String base) {
        Map<String, Object> body = Map.of(
            ExampleConstants.JsonKeys.ID, ExampleConstants.Values.SAMPLE_INVENTORY_ID,
            ExampleConstants.JsonKeys.SKU, ExampleConstants.Values.SAMPLE_SKU,
            ExampleConstants.JsonKeys.QUANTITY, 12,
            ExampleConstants.JsonKeys.WAREHOUSE, ExampleConstants.Values.SAMPLE_WAREHOUSE
        );
        return webClient.post().uri(base
                + HoneycombConstants.Paths.HONEYCOMB_MODELS
                + "/"
                        + ExampleConstants.Cells.INVENTORY
                + "/"
                        + ExampleConstants.Values.ROUTE_ITEMS)
                .header(apiKeyHeader, adminKey)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .doOnNext(resp -> log.info(ExampleConstants.Messages.LOG_CREATE_INVENTORY, resp))
                .then();
    }

    private Mono<Void> listInventory(String base) {
        return webClient.get().uri(base
                + HoneycombConstants.Paths.HONEYCOMB_MODELS
                + "/"
                        + ExampleConstants.Cells.INVENTORY
                + "/"
                        + ExampleConstants.Values.ROUTE_ITEMS)
                .header(apiKeyHeader, adminKey)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .doOnNext(resp -> log.info(ExampleConstants.Messages.LOG_INVENTORY_ITEMS, resp))
                .then();
    }

    private Mono<Void> fetchMetrics(String base) {
        return webClient.get().uri(base
                + HoneycombConstants.Paths.HONEYCOMB_METRICS
                + "/"
                + HoneycombConstants.Paths.CELLS)
                .header(apiKeyHeader, adminKey)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .doOnNext(resp -> log.info(ExampleConstants.Messages.LOG_METRICS, resp))
                .then();
    }

    private Mono<Void> fetchAudit(String base) {
        return webClient.get().uri(base + HoneycombConstants.Paths.HONEYCOMB_AUDIT)
                .header(apiKeyHeader, adminKey)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .doOnNext(resp -> log.info(ExampleConstants.Messages.LOG_AUDIT, resp))
                .then();
    }
}
