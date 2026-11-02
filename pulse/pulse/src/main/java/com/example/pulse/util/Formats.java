package com.example.pulse.util;

public final class Formats {
    private Formats() {}

    /** 250 -> "4m 10s", 3700 -> "1h 1m" */
    public static String duration(long seconds) {
        if (seconds < 60) return seconds + "s";
        long m = seconds / 60, s = seconds % 60;
        if (m < 60) return s == 0 ? m + "m" : m + "m " + s + "s";
        long h = m / 60;
        if (h < 24) return h + "h " + (m % 60) + "m";
        return (h / 24) + "d " + (h % 24) + "h";
    }

    public static String truncate(String text, int max) {
        if (text == null) return null;
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
