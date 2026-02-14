package com.honeycomb.core.client;

import java.util.Map;

/**
 * Strategy interface for mapping a sharedwall envelope map into a typed
 * result object based on the configured {@link SharedwallEnvelopeMode}.
 *
 * @see DefaultSharedwallResponseMapper
 */
public interface SharedwallResponseMapper {
    Object map(Map<String, Object> envelope, SharedwallEnvelopeMode mode, String targetCell);
}

