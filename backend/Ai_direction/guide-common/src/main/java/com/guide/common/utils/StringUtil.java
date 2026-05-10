package com.guide.common.utils;

public final class StringUtil {

    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private StringUtil() {
    }
}
