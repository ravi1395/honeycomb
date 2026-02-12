package com.example.honeycomb.web;

import org.springframework.util.StringUtils;
import com.example.honeycomb.util.HoneycombConstants;

public final class CellPathResolver {
    private CellPathResolver() {}

    public static String resolveCell(String path) {
        if (!StringUtils.hasText(path)) return null;
        String[] parts = path.split(HoneycombConstants.Regex.SLASH);
        for (int i = 0; i < parts.length; i++) {
            if (HoneycombConstants.Paths.MODELS.equals(parts[i]) && i + 1 < parts.length) {
                return parts[i + 1];
            }
            if (HoneycombConstants.Paths.CELLS.equals(parts[i]) && i + 1 < parts.length) {
                // admin routes /honeycomb/cells/{name}
                if (i + 2 < parts.length && HoneycombConstants.Paths.HONEYCOMB_BASE.substring(1).equals(parts[i - 1])) {
                    return parts[i + 1];
                }
                // interaction routes /cells/{from}/invoke/{to}/... or /cells/{from}/forward/{to}
                if (i + 3 < parts.length && (HoneycombConstants.Paths.INVOKE.equals(parts[i + 2]) || HoneycombConstants.Paths.FORWARD.equals(parts[i + 2]))) {
                    return parts[i + 3];
                }
                // fallback to source cell
                return parts[i + 1];
            }
        }
        return null;
    }
}
