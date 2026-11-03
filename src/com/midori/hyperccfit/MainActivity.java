package com.midori.hyperccfit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataOutputStream;

public class MainActivity extends Activity {

    private TextView tvScreenWidth;
    private TextView tvScreenDensity;
    private TextView tvPanelCalcWidth;
    private View statusDot;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        updateStatus();
        updateScreenInfo();
    }

    private void initViews() {
        tvScreenWidth = findViewById(R.id.tv_screen_width);
        tvScreenDensity = findViewById(R.id.tv_screen_density);
        tvPanelCalcWidth = findViewById(R.id.tv_panel_calc_width);
        statusDot = findViewById(R.id.status_dot);
        statusText = findViewById(R.id.status_text);

        Button btnRestart = findViewById(R.id.btn_restart_systemui);
        btnRestart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRestartDialog();
            }
        });
    }

    private void updateStatus() {
        boolean active = isModuleActive();
        if (active) {
            statusDot.setBackgroundColor(Color.parseColor("#34C759"));
            statusText.setText(R.string.status_active);
            statusText.setTextColor(Color.parseColor("#34C759"));
        } else {
            statusDot.setBackgroundColor(Color.parseColor("#FF3B30"));
            statusText.setText(R.string.status_inactive);
            statusText.setTextColor(Color.parseColor("#FF3B30"));
        }
    }

    public boolean isModuleActive() {
        return false;
    }

    private void updateScreenInfo() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        Configuration config = getResources().getConfiguration();

        float density = dm.density;
        int screenWidthPx = Math.min(dm.widthPixels, dm.heightPixels);
        int screenWidthDp = Math.round(screenWidthPx / density);
        if (config.screenWidthDp > 0) {
            screenWidthDp = config.screenWidthDp;
        }

        tvScreenWidth.setText(getString(R.string.screen_width_info, screenWidthDp, screenWidthPx));
        tvScreenDensity.setText(getString(R.string.screen_density_info, dm.densityDpi, density));

        int targetWidth = CCFitCalculator.getTargetPanelWidthPx(getResources());
        int targetWidthDp = Math.round(targetWidth / density);
        float ratio = (targetWidthDp / (float) screenWidthDp) * 100f;

        tvPanelCalcWidth.setText(getString(R.string.panel_width_info, targetWidthDp, ratio));
        tvPanelCalcWidth.setTextColor(Color.parseColor("#0A84FF"));
    }

    private void showRestartDialog() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.action_restart_systemui)
            .setMessage(R.string.restart_confirm_msg)
            .setPositiveButton(R.string.btn_confirm, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    restartSystemUI();
                }
            })
            .setNegativeButton(R.string.btn_cancel, null)
            .show();
    }

    private void restartSystemUI() {
        try {
            Process su = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(su.getOutputStream());
            os.writeBytes("killall com.android.systemui\n");
            os.writeBytes("exit\n");
            os.flush();
            su.waitFor();
        } catch (Throwable t) {
            Toast.makeText(this, R.string.toast_restart_failed, Toast.LENGTH_LONG).show();
        }
    }
}
