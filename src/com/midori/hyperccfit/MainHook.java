package com.midori.hyperccfit;

import android.util.Log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    public static final String TAG = "HyperCCFit_MainHook";
    public static final String TARGET_PKG = "com.android.systemui";
    public static final String SELF_PKG = "com.midori.hyperccfit";
    public static final String TARGET_PLUGIN_CLASS = "miui.systemui.controlcenter.panel.main.MainPanelController";

    private static volatile boolean sPluginHooked = false;

    @Override
    public void initZygote(StartupParam startupParam) {
        try {
            ResourceHook.initHook(null);
        } catch (Throwable t) {
            Log.e(TAG, "Failed in initZygote", t);
        }
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (SELF_PKG.equals(lpparam.packageName)) {
            hookSelf(lpparam.classLoader);
            return;
        }

        if (!TARGET_PKG.equals(lpparam.packageName)) {
            return;
        }

        Log.i(TAG, "Loaded SystemUI process, installing HyperCCFit hooks...");

        // 1. Initialize ResourceHook
        try {
            ResourceHook.initHook(lpparam.classLoader);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to init ResourceHook in SystemUI", t);
        }

        // 2. Check if the target plugin class exists in current ClassLoader
        try {
            Class<?> clazz = XposedHelpers.findClassIfExists(TARGET_PLUGIN_CLASS, lpparam.classLoader);
            if (clazz != null) {
                Log.i(TAG, "Target plugin class found directly in SystemUI ClassLoader.");
                ViewHook.initHook(lpparam.classLoader);
                sPluginHooked = true;
                return;
            }
        } catch (Throwable ignored) {
        }

        // 3. If plugin is not yet loaded, listen dynamically via ClassLoader.loadClass
        try {
            XposedHelpers.findAndHookMethod(
                ClassLoader.class,
                "loadClass",
                String.class,
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (sPluginHooked) return;

                        String className = (String) param.args[0];
                        if (TARGET_PLUGIN_CLASS.equals(className)) {
                            Class<?> loadedClass = (Class<?>) param.getResult();
                            if (loadedClass != null) {
                                ClassLoader pluginLoader = loadedClass.getClassLoader();
                                Log.i(TAG, "Intercepted plugin target class: " + className + ", loader: " + pluginLoader);
                                ViewHook.initHook(pluginLoader);
                                ResourceHook.initHook(pluginLoader);
                                sPluginHooked = true;
                            }
                        }
                    }
                }
            );
            Log.i(TAG, "ClassLoader.loadClass listener registered for MIUISystemUIPlugin.");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook ClassLoader.loadClass", t);
        }
    }

    private void hookSelf(ClassLoader classLoader) {
        try {
            Class<?> mainActivityClass = XposedHelpers.findClassIfExists("com.midori.hyperccfit.MainActivity", classLoader);
            if (mainActivityClass != null) {
                XposedHelpers.findAndHookMethod(
                    mainActivityClass,
                    "isModuleActive",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            param.setResult(true);
                        }
                    }
                );
            }
        } catch (Throwable ignored) {
        }
    }
}
