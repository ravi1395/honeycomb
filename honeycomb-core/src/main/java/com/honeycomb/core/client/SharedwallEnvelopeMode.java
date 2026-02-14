package com.honeycomb.core.client;

/**
 * Envelope unwrapping strategies for multi-cell sharedwall responses.
 *
 * <ul>
 *   <li>{@code RAW} – return the full envelope map as-is</li>
 *   <li>{@code FIRST_RESULT} – extract the first cell’s result</li>
 *   <li>{@code STRICT} – fail if more than one cell responded</li>
 *   <li>{@code MERGED} – merge all cell results into one map</li>
 * </ul>
 */
public enum SharedwallEnvelopeMode {
    RAW_ENVELOPE,
    FIRST_RESULT,
    STRICT_SINGLE_CELL,
    MERGED_RESULTS
}
