package com.example.honeycomb.examples;

import com.example.honeycomb.annotations.Cell;
import com.example.honeycomb.annotations.Sharedwall;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

import java.util.Map;

@Cell
@Component
public class ExampleSharedService {

    @Sharedwall(value = "echo", allowedFrom = {"test-client"})
    public Mono<String> echo(String input) {
        return Mono.just("echo:" + input);
    }

    @Sharedwall
    public Mono<Map<String,Object>> summarize(Map<String,Object> payload) {
        return Mono.just(Map.of(
                "receivedKeys", payload == null ? 0 : payload.keySet().size(),
                "original", payload
        ));
    }

    @Sharedwall("concat")
    public Mono<String> concat(String a, String b) {
        return Mono.just(a + ":" + b);
    }

    @Sharedwall("sumList")
    public Mono<Integer> sumList(java.util.List<Integer> nums) {
        if (nums == null) return Mono.just(0);
        return Mono.just(nums.stream().mapToInt(Integer::intValue).sum());
    }

    @Sharedwall("boom")
    public Mono<Void> boom(String in) {
        if ("boom".equals(in)) return Mono.error(new RuntimeException("boom-exception"));
        return Mono.empty();
    }
}
