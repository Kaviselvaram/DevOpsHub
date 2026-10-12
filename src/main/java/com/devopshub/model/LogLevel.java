package com.devopshub.model;

public enum LogLevel {
    INFO,
    WARN,
    ERROR,
    DEBUG;

    public static LogLevel fromString(String val) {
        if (val == null) return INFO;
        try {
            return LogLevel.valueOf(val.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return INFO;
        }
    }
}
