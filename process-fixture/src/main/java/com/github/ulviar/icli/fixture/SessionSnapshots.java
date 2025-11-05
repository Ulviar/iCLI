package com.github.ulviar.icli.fixture;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds small diagnostic maps for readiness banners.
 */
final class SessionSnapshots {
    private SessionSnapshots() {}

    static Map<String, Object> runtime(RuntimeBounds bounds) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("minMs", bounds.minMillis());
        map.put("maxMs", bounds.maxMillis());
        return map;
    }

    static Map<String, Object> payload(PayloadProfile profile) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", profile.format().name().toLowerCase());
        map.put("size", profile.size());
        return map;
    }
}
