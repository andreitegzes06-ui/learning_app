package com.studymode.ui;

import java.util.Arrays;

public enum NavigationPage {
    DASHBOARD("dashboard", "Dashboard", "Your study space, ready when you are.", 1),
    FOCUS("focus", "Focus Session", "One session. One clear intention.", 3),
    HISTORY("history", "Session History", "Your actual focus time, saved on this computer.", 3),
    CLASSROOMS("classrooms", "Classrooms", "Give every subject a home.", 2),
    WORKSPACES("workspaces", "Workspaces", "A familiar setup for your next study session.", 2),
    BLOCKED("blocked", "Blocked Applications", "Make room for fewer distractions.", 5),
    GOALS("goals", "Goals", "Turn intentions into manageable steps.", 4),
    STATISTICS("statistics", "Statistics", "Understand how you spend your study time.", 4),
    ACHIEVEMENTS("achievements", "Achievements", "Recognise the habits you build.", 6),
    SETTINGS("settings", "Settings", "Make Study Mode yours.", 1);

    private final String key;
    private final String title;
    private final String subtitle;
    private final int milestone;
    NavigationPage(String key, String title, String subtitle, int milestone) {
        this.key = key; this.title = title; this.subtitle = subtitle; this.milestone = milestone;
    }
    public String key() { return key; }
    public String title() { return title; }
    public String subtitle() { return subtitle; }
    public int milestone() { return milestone; }
    public static NavigationPage fromKey(String key) {
        return Arrays.stream(values()).filter(page -> page.key.equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown page: " + key));
    }
}
