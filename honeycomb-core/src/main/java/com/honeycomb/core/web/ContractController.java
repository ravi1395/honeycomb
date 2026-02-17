package com.honeycomb.core.web;

import com.honeycomb.core.contract.ContractExporter;
import com.honeycomb.core.contract.ContractGenerator;
import com.honeycomb.core.contract.ContractVerifier;
import com.honeycomb.core.contract.SharedMethodContract;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * REST controller that exposes contract generation, export, and
 * verification endpoints for Honeycomb shared methods.
 *
 * <p>Activated only when {@code honeycomb.contracts.enabled=true}.</p>
 *
 * @since 1.4.3
 */
@RestController
@RequestMapping("/honeycomb/contracts")
@ConditionalOnProperty(name = "honeycomb.contracts.enabled", havingValue = "true")
@Tag(name = "Contracts", description = "Consumer-driven contract testing for shared methods")
public class ContractController {

    private final ContractGenerator generator;
    private final ContractExporter exporter;
    private final ContractVerifier verifier;

    public ContractController(ContractGenerator generator,
                              ContractExporter exporter,
                              ContractVerifier verifier) {
        this.generator = generator;
        this.exporter = exporter;
        this.verifier = verifier;
    }

    @GetMapping
    @Operation(summary = "List all generated contracts")
    public Mono<ResponseEntity<List<SharedMethodContract>>> listContracts() {
        return Mono.fromCallable(generator::generateAll)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{methodName}")
    @Operation(summary = "Get contract for a specific shared method")
    public Mono<ResponseEntity<SharedMethodContract>> getContract(@PathVariable String methodName) {
        return Mono.fromCallable(() -> generator.generate(methodName))
                .map(opt -> opt.map(ResponseEntity::ok)
                        .orElse(ResponseEntity.notFound().build()));
    }

    @GetMapping("/export")
    @Operation(summary = "Export contracts in configured format (SCC YAML or Pact JSON)")
    public Mono<ResponseEntity<Map<String, String>>> exportContracts() {
        return Mono.fromCallable(generator::generateAll)
                .map(exporter::export)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify supplied contracts against live shared methods")
    public Mono<ResponseEntity<List<ContractVerifier.Violation>>> verify(
            @RequestBody List<SharedMethodContract> contracts) {
        return Mono.fromCallable(() -> verifier.verify(contracts))
                .map(violations -> violations.isEmpty()
                        ? ResponseEntity.ok(violations)
                        : ResponseEntity.status(409).body(violations));
    }

    @PostMapping("/verify/live")
    @Operation(summary = "Generate and self-verify all contracts")
    public Mono<ResponseEntity<Map<String, Object>>> selfVerify() {
        return Mono.fromCallable(() -> {
            List<SharedMethodContract> contracts = generator.generateAll();
            List<ContractVerifier.Violation> violations = verifier.verify(contracts);
            return Map.<String, Object>of(
                    "contractCount", contracts.size(),
                    "violations", violations,
                    "status", violations.isEmpty() ? "PASSED" : "FAILED"
            );
        }).map(ResponseEntity::ok);
    }
}
