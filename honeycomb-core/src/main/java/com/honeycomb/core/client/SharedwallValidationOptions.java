package com.honeycomb.core.client;

/**
 * Immutable options controlling validation behaviour for sharedwall method
 * invocations.
 *
 * @param failOnDeprecated   if {@code true}, invoking a deprecated method throws an error
 * @param enforceAllowedFrom if {@code true}, the calling cell must be in the method's
 *                           {@code allowedFrom} list
 */
public record SharedwallValidationOptions(
        boolean failOnDeprecated,
        boolean enforceAllowedFrom
) {
    public static SharedwallValidationOptions defaults() {
        return new SharedwallValidationOptions(false, true);
    }
}
