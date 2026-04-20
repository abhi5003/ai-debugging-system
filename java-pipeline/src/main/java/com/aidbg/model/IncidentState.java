package com.aidbg.model;

public enum IncidentState {
    NEW, IN_PROGRESS, ON_HOLD, RESOLVED, CLOSED, CANCELLED;

    public static IncidentState fromServiceNow(String code) {
        if (code == null) return NEW;
        return switch (code.trim()) {
            case "1" -> NEW;
            case "2" -> IN_PROGRESS;
            case "3" -> ON_HOLD;
            case "6" -> RESOLVED;
            case "7" -> CLOSED;
            case "8" -> CANCELLED;
            default  -> NEW;
        };
    }
}
