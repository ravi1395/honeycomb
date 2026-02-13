package com.honeycomb.core.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Simple utility to fetch generated sharedwall interface stubs and write them into source folders.
 *
 * Usage:
 * java com.honeycomb.core.client.SharedwallStubGenerator \
 *   http://localhost:8080 src/main/java/com/example/client/generated/SharedwallApi.java \
 *   SharedwallApi com.example.client.generated
 */
public final class SharedwallStubGenerator {
    private SharedwallStubGenerator() {}

    public static void main(String[] args) throws IOException {
        if (args.length < 4) {
            throw new IllegalArgumentException("Usage: <baseUrl> <outputFile> <interfaceName> <packageName>");
        }
        String baseUrl = Objects.requireNonNull(args[0]);
        Path outputFile = Paths.get(args[1]);
        String interfaceName = Objects.requireNonNull(args[2]);
        String packageName = Objects.requireNonNull(args[3]);

        String stub = fetchStub(baseUrl, interfaceName, packageName);
        write(outputFile, stub);
    }

    public static String fetchStub(String baseUrl, String interfaceName, String packageName) {
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String endpoint = normalizedBase + "/honeycomb/shared/methods/stub"
                + "?interfaceName=" + interfaceName
                + "&packageName=" + packageName;

        org.springframework.web.reactive.function.client.WebClient webClient =
                org.springframework.web.reactive.function.client.WebClient.builder().build();
        return webClient.get()
                .uri(endpoint)
                .retrieve()
                .bodyToMono(String.class)
                .blockOptional()
                .orElse("");
    }

    public static void write(Path outputFile, String content) throws IOException {
        Path parent = outputFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(outputFile, content, StandardCharsets.UTF_8);
    }
}
