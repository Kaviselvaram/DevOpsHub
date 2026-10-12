package com.devopshub.model;

public enum TriggerType {
    MANUAL,
    WEBHOOK,
    SCHEDULED;

    public static TriggerType fromString(String val) {
        if (val == null) return MANUAL;
        try {
            return TriggerType.valueOf(val.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return MANUAL;
        }
    }
}
