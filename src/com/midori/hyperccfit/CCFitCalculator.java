package com.midori.hyperccfit;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.util.Log;

public class CCFitCalculator {
    public static final String TAG = "HyperCCFit_Calc";

    public static final String DIMEN_PANEL_WIDTH = "control_center_panel_width";
    public static final String DIMEN_4_ROWS_WITH_MARGIN = "control_center_universal_4_rows_with_margin_size";
    public static final String DIMEN_4_ROWS = "control_center_universal_4_rows_size";
    public static final String DIMEN_3_ROWS_WITH_MARGIN = "control_center_universal_3_rows_with_margin_size";
    public static final String DIMEN_2_ROWS = "control_center_universal_2_rows_size";
    public static final String DIMEN_1_ROW = "control_center_universal_1_row_size";
    public static final String DIMEN_SECONDARY_WIDTH = "control_center_secondary_panel_content_width";

    public static final float MARGIN_DP = 7.0f;
    public static final float SLIDER_WIDTH_DP = 64.0f; // Slender slider width for 3 sliders in row 2
    public static final float ROW2_HEIGHT_FACTOR = 0.78f; // Further fine-tuned Row 2 height (Media card & Sliders) by ~22% for optimal proportion

    public static boolean isTargetDimen(String resName) {
        if (resName == null) return false;
        return resName.equals(DIMEN_4_ROWS_WITH_MARGIN) ||
               resName.equals(DIMEN_PANEL_WIDTH) ||
               resName.equals(DIMEN_4_ROWS) ||
               resName.equals(DIMEN_3_ROWS_WITH_MARGIN) ||
               resName.equals(DIMEN_2_ROWS) ||
               resName.equals(DIMEN_1_ROW) ||
               resName.equals(DIMEN_SECONDARY_WIDTH);
    }

    public static float calculateDimensionDp(String resName, Resources res) {
        if (!ConfigManager.isEnabled()) {
            return -1f;
        }

        Configuration config = res.getConfiguration();
        if (config == null || config.orientation != Configuration.ORIENTATION_PORTRAIT) {
            return -1f;
        }

        DisplayMetrics dm = res.getDisplayMetrics();
        float density = dm.density;
        if (density <= 0) return -1f;

        float screenWidthDp = Math.min(dm.widthPixels, dm.heightPixels) / density;
        if (config.screenWidthDp > 0) {
            screenWidthDp = config.screenWidthDp;
        }

        if (screenWidthDp <= 380f) {
            return -1f;
        }

        // Expanded panel width: 16dp total screen margins (8dp each side)
        float targetPanelWidthDp = screenWidthDp - 16f;
        if (targetPanelWidthDp < 342f) targetPanelWidthDp = 342f;

        float colWithMargin = targetPanelWidthDp / 4.0f;

        if (resName.equals(DIMEN_4_ROWS_WITH_MARGIN) || resName.equals(DIMEN_PANEL_WIDTH)) {
            return targetPanelWidthDp;
        } else if (resName.equals(DIMEN_4_ROWS) || resName.equals(DIMEN_SECONDARY_WIDTH)) {
            return targetPanelWidthDp - (2 * MARGIN_DP);
        } else if (resName.equals(DIMEN_3_ROWS_WITH_MARGIN)) {
            return colWithMargin * 3.0f;
        } else if (resName.equals(DIMEN_2_ROWS)) {
            // Row 2 height (Media Player & Sliders): reduce slightly by 15% for optimal proportion
            float base2RowsDp = (colWithMargin * 2.0f) - (2 * MARGIN_DP);
            return base2RowsDp * ROW2_HEIGHT_FACTOR;
        } else if (resName.equals(DIMEN_1_ROW)) {
            return SLIDER_WIDTH_DP;
        }

        return -1f;
    }

    public static int calculateDimensionPx(String resName, Resources res) {
        float dp = calculateDimensionDp(resName, res);
        if (dp < 0) return -1;
        float density = res.getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    public static int getTargetPanelWidthPx(Resources res) {
        return calculateDimensionPx(DIMEN_4_ROWS_WITH_MARGIN, res);
    }
}
