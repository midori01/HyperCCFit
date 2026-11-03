package com.midori.hyperccfit;

public class ConfigManager {
    public static final String PACKAGE_NAME = "com.midori.hyperccfit";
    public static final int DEFAULT_PANEL_WIDTH_DP = 468;

    public static boolean isEnabled() {
        return true;
    }

    public static int getTargetPanelWidthDp() {
        return DEFAULT_PANEL_WIDTH_DP;
    }

    public static boolean isTile5ColumnsEnabled() {
        return true;
    }

    public static boolean isSlenderSlidersEnabled() {
        return true;
    }

    public static boolean isRingerSliderEnabled() {
        return true;
    }
}
