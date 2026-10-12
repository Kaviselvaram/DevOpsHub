package com.devopshub.model;

public enum LogStream {
    STDOUT,
    STDERR;

    public static LogStream fromString(String val) {
        if (val == null) return STDOUT;
        try {
            return LogStream.valueOf(val.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return STDOUT;
        }
    }
}
