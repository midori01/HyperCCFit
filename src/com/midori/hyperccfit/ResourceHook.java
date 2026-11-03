package com.midori.hyperccfit;

import android.content.res.Resources;
import android.util.Log;

import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ResourceHook {
    private static final String TAG = "HyperCCFit_ResHook";
    private static final ConcurrentHashMap<Integer, String> sResIdCache = new ConcurrentHashMap<>();
    private static boolean sHooked = false;

    public static void initHook(ClassLoader classLoader) {
        if (sHooked) return;
        sHooked = true;

        try {
            XposedHelpers.findAndHookMethod(
                Resources.class,
                "getDimensionPixelSize",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        int id = (int) param.args[0];
                        if ((id >>> 24) != 0x7f) return;

                        Resources res = (Resources) param.thisObject;
                        String name = getOrResolveResName(res, id);
                        if (name != null && CCFitCalculator.isTargetDimen(name)) {
                            int customPx = CCFitCalculator.calculateDimensionPx(name, res);
                            if (customPx > 0) {
                                param.setResult(customPx);
                            }
                        }
                    }
                }
            );

            XposedHelpers.findAndHookMethod(
                Resources.class,
                "getDimensionPixelOffset",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        int id = (int) param.args[0];
                        if ((id >>> 24) != 0x7f) return;

                        Resources res = (Resources) param.thisObject;
                        String name = getOrResolveResName(res, id);
                        if (name != null && CCFitCalculator.isTargetDimen(name)) {
                            int customPx = CCFitCalculator.calculateDimensionPx(name, res);
                            if (customPx > 0) {
                                param.setResult(customPx);
                            }
                        }
                    }
                }
            );

            XposedHelpers.findAndHookMethod(
                Resources.class,
                "getDimension",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        int id = (int) param.args[0];
                        if ((id >>> 24) != 0x7f) return;

                        Resources res = (Resources) param.thisObject;
                        String name = getOrResolveResName(res, id);
                        if (name != null && CCFitCalculator.isTargetDimen(name)) {
                            float customDp = CCFitCalculator.calculateDimensionDp(name, res);
                            if (customDp > 0) {
                                float customPx = customDp * res.getDisplayMetrics().density;
                                param.setResult(customPx);
                            }
                        }
                    }
                }
            );

            Log.i(TAG, "Successfully hooked Resources dimension methods.");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook Resources methods", t);
        }
    }

    private static String getOrResolveResName(Resources res, int id) {
        String cached = sResIdCache.get(id);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }

        try {
            String entryName = res.getResourceEntryName(id);
            if (CCFitCalculator.isTargetDimen(entryName)) {
                sResIdCache.put(id, entryName);
                Log.d(TAG, "Matched target dimen: " + entryName + " (id=" + Integer.toHexString(id) + ")");
                return entryName;
            } else {
                sResIdCache.put(id, "");
                return null;
            }
        } catch (Throwable ignored) {
            sResIdCache.put(id, "");
            return null;
        }
    }
}
