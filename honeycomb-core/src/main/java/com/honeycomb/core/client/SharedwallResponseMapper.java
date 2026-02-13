package com.honeycomb.core.client;

import java.util.Map;

public interface SharedwallResponseMapper {
    Object map(Map<String, Object> envelope, SharedwallEnvelopeMode mode, String targetCell);
}

