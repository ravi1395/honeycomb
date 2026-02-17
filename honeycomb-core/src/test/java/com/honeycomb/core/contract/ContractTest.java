package com.honeycomb.core.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.honeycomb.core.annotations.Sharedwall;
import com.honeycomb.core.config.HoneycombContractProperties;
import com.honeycomb.core.service.SharedwallMethodCache;
import com.honeycomb.core.service.SharedwallMethodCache.MethodCandidate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ContractGenerator} and {@link ContractVerifier}.
 *
 * @since 1.4.3
 */
class ContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SharedwallMethodCache methodCache;
    private HoneycombContractProperties properties;

    @BeforeEach
    void setUp() {
        methodCache = mock(SharedwallMethodCache.class);
        properties = new HoneycombContractProperties();
    }

    // ---- fixtures -----

    /** Dummy bean with @Sharedwall methods for contract testing. */
    @SuppressWarnings("unused")
    static class SampleCell {
        @Sharedwall(value = "greet", version = "v1")
        public String greet(String name, int times) {
            return name.repeat(times);
        }

        @Sharedwall(value = "restricted", version = "v2", allowedFrom = {"cell-a", "cell-b"})
        public void restricted(String data) {
        }
    }

    private MethodCandidate candidateFor(String methodName, Class<?>... paramTypes) throws Exception {
        Method m = SampleCell.class.getMethod(methodName, paramTypes);
        return new MethodCandidate(new SampleCell(), m, m.getAnnotation(Sharedwall.class),
                objectMapper, new SimpleMeterRegistry());
    }

    // ================================================================
    //  ContractGenerator tests
    // ================================================================
    @Nested
    @DisplayName("ContractGenerator")
    class GeneratorTests {

        private ContractGenerator generator;

        @BeforeEach
        void init() {
            generator = new ContractGenerator(methodCache, properties, objectMapper);
        }

        @Test
        @DisplayName("generate() builds contract with parameters and return type")
        void generateSingle() throws Exception {
            MethodCandidate candidate = candidateFor("greet", String.class, int.class);
            when(methodCache.getCandidates("greet")).thenReturn(List.of(candidate));

            Optional<SharedMethodContract> opt = generator.generate("greet");

            assertTrue(opt.isPresent());
            SharedMethodContract c = opt.get();
            assertEquals("greet", c.methodName());
            assertEquals("v1", c.version());
            assertEquals(2, c.parameters().size());
            assertEquals("java.lang.String", c.returnType());
        }

        @Test
        @DisplayName("generate() returns empty for unknown method")
        void generateUnknown() {
            when(methodCache.getCandidates("nope")).thenReturn(List.of());

            Optional<SharedMethodContract> opt = generator.generate("nope");

            assertFalse(opt.isPresent());
        }

        @Test
        @DisplayName("generateAll() returns contracts for all cached methods")
        void generateAll() throws Exception {
            MethodCandidate greet = candidateFor("greet", String.class, int.class);
            MethodCandidate restricted = candidateFor("restricted", String.class);
            when(methodCache.getAllCandidates()).thenReturn(Map.of(
                    "greet", List.of(greet),
                    "restricted", List.of(restricted)));

            List<SharedMethodContract> contracts = generator.generateAll();

            assertEquals(2, contracts.size());
        }

        @Test
        @DisplayName("contract includes allowedFrom constraints")
        void allowedFrom() throws Exception {
            MethodCandidate restricted = candidateFor("restricted", String.class);
            when(methodCache.getCandidates("restricted")).thenReturn(List.of(restricted));

            SharedMethodContract c = generator.generate("restricted").orElseThrow();

            assertTrue(c.allowedFrom().contains("cell-a"));
            assertTrue(c.allowedFrom().contains("cell-b"));
        }
    }

    // ================================================================
    //  ContractVerifier tests
    // ================================================================
    @Nested
    @DisplayName("ContractVerifier")
    class VerifierTests {

        private ContractVerifier verifier;

        @BeforeEach
        void init() {
            verifier = new ContractVerifier(methodCache, objectMapper);
        }

        @Test
        @DisplayName("no violations when contract matches live method")
        void noViolations() throws Exception {
            MethodCandidate candidate = candidateFor("greet", String.class, int.class);
            when(methodCache.getCandidates("greet", "v1")).thenReturn(List.of(candidate));

            SharedMethodContract contract = SharedMethodContract.builder()
                    .methodName("greet")
                    .version("v1")
                    .addParameter("name", "java.lang.String", true)
                    .addParameter("times", "int", false)
                    .returnType("java.lang.String")
                    .build();

            List<ContractVerifier.Violation> violations = verifier.verify(List.of(contract));

            assertTrue(violations.isEmpty(), "expected no violations: " + violations);
        }

        @Test
        @DisplayName("METHOD_MISSING when method does not exist")
        void methodMissing() {
            when(methodCache.getCandidates("gone", "v1")).thenReturn(List.of());

            SharedMethodContract contract = SharedMethodContract.builder()
                    .methodName("gone")
                    .version("v1")
                    .returnType("void")
                    .build();

            List<ContractVerifier.Violation> violations = verifier.verify(List.of(contract));

            assertEquals(1, violations.size());
            assertEquals("METHOD_MISSING", violations.getFirst().code());
        }

        @Test
        @DisplayName("PARAM_COUNT_MISMATCH when parameter count differs")
        void paramCountMismatch() throws Exception {
            MethodCandidate candidate = candidateFor("greet", String.class, int.class);
            when(methodCache.getCandidates("greet", "v1")).thenReturn(List.of(candidate));

            SharedMethodContract contract = SharedMethodContract.builder()
                    .methodName("greet")
                    .version("v1")
                    .addParameter("name", "java.lang.String", true)
                    // only 1 param instead of 2
                    .returnType("java.lang.String")
                    .build();

            List<ContractVerifier.Violation> violations = verifier.verify(List.of(contract));

            assertEquals(1, violations.size());
            assertEquals("PARAM_COUNT_MISMATCH", violations.getFirst().code());
        }

        @Test
        @DisplayName("PARAM_TYPE_MISMATCH when parameter type differs")
        void paramTypeMismatch() throws Exception {
            MethodCandidate candidate = candidateFor("greet", String.class, int.class);
            when(methodCache.getCandidates("greet", "v1")).thenReturn(List.of(candidate));

            SharedMethodContract contract = SharedMethodContract.builder()
                    .methodName("greet")
                    .version("v1")
                    .addParameter("name", "java.lang.String", true)
                    .addParameter("times", "java.lang.Long", false) // wrong type
                    .returnType("java.lang.String")
                    .build();

            List<ContractVerifier.Violation> violations = verifier.verify(List.of(contract));

            assertEquals(1, violations.size());
            assertEquals("PARAM_TYPE_MISMATCH", violations.getFirst().code());
        }

        @Test
        @DisplayName("ACCESS_CONSTRAINT_CHANGED when allowedFrom differs")
        void accessConstraintChanged() throws Exception {
            MethodCandidate candidate = candidateFor("restricted", String.class);
            when(methodCache.getCandidates("restricted", "v2")).thenReturn(List.of(candidate));

            SharedMethodContract contract = SharedMethodContract.builder()
                    .methodName("restricted")
                    .version("v2")
                    .addParameter("data", "java.lang.String", true)
                    .returnType("void")
                    .addAllowedFrom("cell-a") // missing cell-b
                    .build();

            List<ContractVerifier.Violation> violations = verifier.verify(List.of(contract));

            assertTrue(violations.stream().anyMatch(v -> "ACCESS_CONSTRAINT_CHANGED".equals(v.code())),
                    "expected ACCESS_CONSTRAINT_CHANGED: " + violations);
        }
    }
}
