package com.example.honeycomb.examples;

import com.example.honeycomb.annotations.Cell;
import com.example.honeycomb.annotations.Sharedwall;
import org.springframework.stereotype.Component;

import java.util.Map;

@Cell
@Component
public class ExampleSharedService {

    @Sharedwall(value = "echo", allowedFrom = {"test-client"})
    public String echo(String input) {
        return "echo:" + input;
    }

    @Sharedwall
    public Map<String,Object> summarize(Map<String,Object> payload) {
        return Map.of(
                "receivedKeys", payload == null ? 0 : payload.keySet().size(),
                "original", payload
        );
    }

    @Sharedwall("concat")
    public String concat(String a, String b) {
        return a + ":" + b;
    }

    @Sharedwall("sumList")
    public int sumList(java.util.List<Integer> nums) {
        if (nums == null) return 0;
        return nums.stream().mapToInt(Integer::intValue).sum();
    }

    @Sharedwall("boom")
    public void boom(String in) {
        if ("boom".equals(in)) throw new RuntimeException("boom-exception");
    }
}
