package com.example.honeycomb.client;

public record SharedwallValidationOptions(
        boolean failOnDeprecated,
        boolean enforceAllowedFrom
) {
    public static SharedwallValidationOptions defaults() {
        return new SharedwallValidationOptions(false, true);
    }
}
