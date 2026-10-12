package com.devopshub.model;

public enum StageType {
    CHECKOUT,
    BUILD,
    TEST,
    SECURITY,
    PACKAGE,
    DEPLOY;

    public static StageType fromString(String val) {
        if (val == null) return BUILD;
        try {
            return StageType.valueOf(val.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return BUILD;
        }
    }
}
