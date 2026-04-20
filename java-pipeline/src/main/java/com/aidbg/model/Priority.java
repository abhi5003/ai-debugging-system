package com.aidbg.model;

public enum Priority {
    CRITICAL, HIGH, MEDIUM, LOW, PLANNING;

    public static Priority fromServiceNow(String code) {
        if (code == null) return MEDIUM;
        return switch (code.trim()) {
            case "1" -> CRITICAL;
            case "2" -> HIGH;
            case "3" -> MEDIUM;
            case "4" -> LOW;
            case "5" -> PLANNING;
            default  -> MEDIUM;
        };
    }
}
