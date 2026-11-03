package com.midori.hyperccfit;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;

import de.robv.android.xposed.XposedHelpers;

public class RingerSliderManager {
    private static final String TAG = "HyperCCFit_Ringer";

    public static final int TYPE_RINGER_SLIDER = 865270;
    public static final int PRIORITY_RINGER_SLIDER = 33;
    public static final int SPAN_SIZE = 5;

    public static final int RES_ID_RINGER_ICON = 0x7f080599;      // ic_miui_volume_ringer
    public static final int RES_ID_RINGER_MUTE_ICON = 0x7f08059a; // ic_miui_volume_ringer_mute

    public static final int COLOR_ID_ICON_NORMAL = 0x7f060936;    // toggle_slider_icon_color
    public static final int COLOR_ID_ICON_VOLUME = 0x7f06093b;    // toggle_slider_volume_icon_color

    private static Object sRingerControllerProxy = null;
    private static Object sVolumeSliderController = null;
    private static Context sContext = null;

    private static AudioManager sAudioManager = null;
    private static Handler sMainHandler = null;

    private static Object sCurrentHolder = null;
    private static boolean sIsDragging = false;
    private static int sLastNonZeroVolume = -1;

    private static BroadcastReceiver sVolumeReceiver = null;
    private static SeekBar sCurrentSlider = null;
    private static View sCurrentIconView = null;

    public static synchronized void setVolumeSliderController(Object volumeController, Context context) {
        sVolumeSliderController = volumeController;
        if (context != null) {
            sContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
            if (sAudioManager == null) {
                sAudioManager = (AudioManager) sContext.getSystemService(Context.AUDIO_SERVICE);
            }
        }
        if (sMainHandler == null) {
            sMainHandler = new Handler(Looper.getMainLooper());
        }
    }

    public static synchronized Object getOrCreateRingerController(ClassLoader classLoader) {
        if (!ConfigManager.isRingerSliderEnabled()) {
            return null;
        }

        if (sRingerControllerProxy != null) {
            return sRingerControllerProxy;
        }

        try {
            Class<?> mainPanelContentClass = XposedHelpers.findClass(
                "miui.systemui.controlcenter.panel.main.MainPanelContent",
                classLoader
            );
            Class<?> mainPanelListItemClass = XposedHelpers.findClass(
                "miui.systemui.controlcenter.panel.main.recyclerview.MainPanelListItem",
                classLoader
            );

            InvocationHandler handler = new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    String name = method.getName();

                    if ("available".equals(name)) {
                        return true;
                    }
                    if ("getPriority".equals(name)) {
                        return PRIORITY_RINGER_SLIDER;
                    }
                    if ("getRightOrLeft".equals(name)) {
                        return true;
                    }
                    if ("getType".equals(name)) {
                        return TYPE_RINGER_SLIDER;
                    }
                    if ("getSpanSize".equals(name)) {
                        return SPAN_SIZE;
                    }
                    if ("getListItems".equals(name)) {
                        return Collections.singletonList(proxy);
                    }
                    if ("createViewHolder".equals(name)) {
                        if (args != null && args.length >= 2 && args[1] instanceof Number) {
                            int requestedType = ((Number) args[1]).intValue();
                            if (requestedType != TYPE_RINGER_SLIDER) {
                                return null;
                            }
                        } else {
                            return null;
                        }
                        ViewGroup parent = (ViewGroup) args[0];
                        return createViewHolder(parent);
                    }
                    if ("onBindViewHolder".equals(name)) {
                        if (args != null && args.length > 0 && args[0] != null) {
                            bindViewHolder(args[0]);
                        } else if (sCurrentHolder != null) {
                            bindViewHolder(sCurrentHolder);
                        }
                        return null;
                    }
                    if ("onViewAttachedToWindow".equals(name)) {
                        onViewAttached();
                        return null;
                    }
                    if ("onViewDetachedFromWindow".equals(name)) {
                        onViewDetached();
                        return null;
                    }
                    if ("getHolder".equals(name)) {
                        return sCurrentHolder;
                    }
                    if ("setHolder".equals(name)) {
                        if (args != null && args.length > 0) {
                            sCurrentHolder = args[0];
                        }
                        return null;
                    }
                    if ("onSpreadChange".equals(name)) {
                        if (args != null && args.length >= 3 && args[0] != null) {
                            try {
                                Object holder = args[0];
                                float scale = ((Number) args[1]).floatValue();
                                float transX = ((Number) args[2]).floatValue();
                                XposedHelpers.callMethod(holder, "setScaleFactor", scale);
                                XposedHelpers.callMethod(holder, "setSpreadSlideTransX", transX);
                                XposedHelpers.callMethod(holder, "onFrameCallback");
                            } catch (Throwable ignored) {}
                        }
                        return null;
                    }
                    if ("onExpandChange".equals(name)) {
                        if (args != null && args.length >= 2 && args[0] != null) {
                            try {
                                Object holder = args[0];
                                float ty = ((Number) args[1]).floatValue();
                                View iv = (View) XposedHelpers.getObjectField(holder, "itemView");
                                if (iv != null) iv.setTranslationY(ty);
                            } catch (Throwable ignored) {}
                        }
                        return null;
                    }
                    if ("updateConfiguration".equals(name)) {
                        if (args != null && args.length >= 3 && args[0] != null && args[2] != null) {
                            try {
                                XposedHelpers.callMethod(args[0], "updateConfiguration", args[2]);
                            } catch (Throwable ignored) {}
                        }
                        syncFromSystem(sCurrentSlider, sCurrentIconView);
                        return null;
                    }
                    if ("updateMode".equals(name) || "updateStyle".equals(name)) {
                        if (args != null && args.length > 0 && args[0] != null) {
                            try {
                                if ("updateMode".equals(name) && args.length >= 4) {
                                    XposedHelpers.callMethod(args[0], "updateMode", args[2], args[3]);
                                } else if ("updateStyle".equals(name) && args.length >= 3) {
                                    XposedHelpers.callMethod(args[0], "updateStyle", args[2]);
                                }
                            } catch (Throwable ignored) {}
                        }
                        syncFromSystem(sCurrentSlider, sCurrentIconView);
                        return null;
                    }
                    if ("onUnbindViewHolder".equals(name)) {
                        return null;
                    }
                    if ("equals".equals(name)) {
                        return proxy == (args != null && args.length > 0 ? args[0] : null);
                    }
                    if ("hashCode".equals(name)) {
                        return System.identityHashCode(proxy);
                    }
                    if ("toString".equals(name)) {
                        return "RingerSliderController@Proxy";
                    }

                    Class<?> returnType = method.getReturnType();
                    if (returnType == boolean.class) return false;
                    if (returnType == int.class) return 0;
                    if (returnType == float.class) return 0f;
                    if (returnType == long.class) return 0L;
                    return null;
                }
            };

            sRingerControllerProxy = Proxy.newProxyInstance(
                classLoader,
                new Class<?>[]{ mainPanelContentClass, mainPanelListItemClass },
                handler
            );

            Log.i(TAG, "RingerSliderController proxy created successfully.");
            return sRingerControllerProxy;
        } catch (Throwable t) {
            Log.e(TAG, "Failed to create RingerSliderController proxy", t);
            return null;
        }
    }

    private static Object createViewHolder(ViewGroup parent) {
        try {
            if (sVolumeSliderController == null && parent != null) {
                try {
                    Object adapter = XposedHelpers.callMethod(parent, "getAdapter");
                    if (adapter != null) {
                        java.util.List<?> contentList = (java.util.List<?>) XposedHelpers.getObjectField(adapter, "contentList");
                        if (contentList != null) {
                            for (Object item : contentList) {
                                if (item != null && item.getClass().getName().contains("VolumeSliderController")) {
                                    setVolumeSliderController(item, parent.getContext());
                                    break;
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }

            if (sVolumeSliderController != null && parent != null) {
                Object holder = XposedHelpers.callMethod(sVolumeSliderController, "createViewHolder", parent, 865269);
                if (holder != null) {
                    Log.d(TAG, "createViewHolder: Created fresh ToggleSliderViewHolder for ringer via VolumeSliderController.");
                    return holder;
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error in createViewHolder for ringer slider", t);
        }
        return null;
    }

    private static void bindViewHolder(Object holder) {
        sCurrentHolder = holder;
        if (holder == null) return;

        try {
            if (sMainHandler == null) {
                sMainHandler = new Handler(Looper.getMainLooper());
            }

            View itemView = (View) XposedHelpers.getObjectField(holder, "itemView");
            if (itemView != null) {
                Context ctx = itemView.getContext();
                sContext = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
                if (sAudioManager == null) {
                    sAudioManager = (AudioManager) sContext.getSystemService(Context.AUDIO_SERVICE);
                }
            }

            if (sVolumeSliderController == null && itemView != null && itemView.getParent() instanceof ViewGroup) {
                try {
                    ViewGroup parent = (ViewGroup) itemView.getParent();
                    Object adapter = XposedHelpers.callMethod(parent, "getAdapter");
                    if (adapter != null) {
                        java.util.List<?> contentList = (java.util.List<?>) XposedHelpers.getObjectField(adapter, "contentList");
                        if (contentList != null) {
                            for (Object item : contentList) {
                                if (item != null && item.getClass().getName().contains("VolumeSliderController")) {
                                    setVolumeSliderController(item, itemView.getContext());
                                    break;
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }

            Object binding = XposedHelpers.callMethod(holder, "getBinding");
            if (binding == null) return;

            final SeekBar slider = (SeekBar) XposedHelpers.getObjectField(binding, "slider");
            final View iconView = (View) XposedHelpers.getObjectField(binding, "icon");

            if (slider == null || sAudioManager == null) return;

            sCurrentSlider = slider;
            sCurrentIconView = iconView;

            final int maxVol = sAudioManager.getStreamMaxVolume(AudioManager.STREAM_RING);
            slider.setMax(maxVol * 100);

            int currentVol = sAudioManager.getStreamVolume(AudioManager.STREAM_RING);
            slider.setProgress(currentVol * 100);

            updateIcon(iconView, currentVol);

            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && sAudioManager != null) {
                        int targetVol = Math.round(((float) progress / seekBar.getMax()) * maxVol);
                        try {
                            sAudioManager.setStreamVolume(AudioManager.STREAM_RING, targetVol, AudioManager.FLAG_PLAY_SOUND);
                        } catch (Throwable ignored) {}
                        updateIcon(iconView, targetVol);
                        if (targetVol > 0) {
                            sLastNonZeroVolume = targetVol;
                        }
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    sIsDragging = true;
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    sIsDragging = false;
                    syncFromSystem(slider, iconView);
                }
            });

            if (iconView != null) {
                iconView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (sAudioManager == null) return;
                        int cur = sAudioManager.getStreamVolume(AudioManager.STREAM_RING);
                        if (cur > 0) {
                            sLastNonZeroVolume = cur;
                            try {
                                sAudioManager.setStreamVolume(AudioManager.STREAM_RING, 0, AudioManager.FLAG_PLAY_SOUND);
                            } catch (Throwable ignored) {}
                        } else {
                            int restore = (sLastNonZeroVolume > 0) ? sLastNonZeroVolume : (maxVol * 2 / 3);
                            try {
                                sAudioManager.setStreamVolume(AudioManager.STREAM_RING, restore, AudioManager.FLAG_PLAY_SOUND);
                            } catch (Throwable ignored) {}
                        }
                        syncFromSystem(slider, iconView);
                    }
                });
            }

            registerReceiver();
            Log.d(TAG, "bindViewHolder: Ringer slider bound successfully (vol=" + currentVol + "/" + maxVol + ")");
        } catch (Throwable t) {
            Log.e(TAG, "Error in bindViewHolder", t);
        }
    }

    private static void updateIcon(View iconView, int volume) {
        if (iconView == null) return;
        try {
            Context context = iconView.getContext();
            Context pluginContext = null;
            if (sVolumeSliderController != null) {
                try {
                    pluginContext = (Context) XposedHelpers.callMethod(sVolumeSliderController, "getContext");
                } catch (Throwable ignored) {}
            }
            if (pluginContext == null) {
                pluginContext = context;
            }

            Resources res = (pluginContext != null) ? pluginContext.getResources() : iconView.getResources();
            String pkg = (pluginContext != null) ? pluginContext.getPackageName() : "miui.systemui.plugin";

            // 1. Resolve exact system bell icon from MIUISystemUIPlugin resources
            int resId = 0;
            String iconName = (volume > 0) ? "ic_miui_volume_ringer" : "ic_miui_volume_ringer_mute";

            if (res != null) {
                resId = res.getIdentifier(iconName, "drawable", pkg);
                if (resId == 0) {
                    resId = res.getIdentifier(iconName, "drawable", "miui.systemui.plugin");
                }
                if (resId == 0) {
                    String altName = (volume > 0) ? "ic_audio_ring_notif" : "ic_audio_ring_notif_mute";
                    resId = res.getIdentifier(altName, "drawable", pkg);
                    if (resId == 0) {
                        resId = res.getIdentifier(altName, "drawable", "miui.systemui.plugin");
                    }
                }
            }
            if (resId == 0) {
                resId = (volume > 0) ? RES_ID_RINGER_ICON : RES_ID_RINGER_MUTE_ICON;
            }

            // 2. Set image on ImageView / AnimateColorView
            if (iconView instanceof ImageView) {
                ImageView iv = (ImageView) iconView;
                iv.setImageResource(resId);

                if (iv.getDrawable() == null && res != null) {
                    try {
                        Drawable d = res.getDrawable(resId, pluginContext != null ? pluginContext.getTheme() : context.getTheme());
                        if (d != null) {
                            iv.setImageDrawable(d);
                        }
                    } catch (Throwable ignored) {}
                }

                // Module fallback if plugin resources failed to inflate
                if (iv.getDrawable() == null) {
                    try {
                        Context moduleContext = context.createPackageContext(
                            "com.midori.hyperccfit",
                            Context.CONTEXT_IGNORE_SECURITY
                        );
                        if (moduleContext != null) {
                            int localId = moduleContext.getResources().getIdentifier(
                                (volume > 0) ? "ic_audio_ring_notif" : "ic_audio_ring_notif_mute",
                                "drawable",
                                "com.midori.hyperccfit"
                            );
                            if (localId != 0) {
                                Drawable localD = moduleContext.getDrawable(localId);
                                if (localD != null) {
                                    iv.setImageDrawable(localD);
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }

            // 3. Resolve toggle slider color resources
            int normalColorRes = 0;
            int volumeColorRes = 0;
            if (res != null) {
                normalColorRes = res.getIdentifier("toggle_slider_icon_color", "color", pkg);
                volumeColorRes = res.getIdentifier("toggle_slider_volume_icon_color", "color", pkg);
            }
            if (normalColorRes == 0) normalColorRes = COLOR_ID_ICON_NORMAL;
            if (volumeColorRes == 0) volumeColorRes = COLOR_ID_ICON_VOLUME;

            // 4. Apply HyperOS blur blend colors or system tint via AnimateColorView
            boolean blendApplied = false;
            try {
                ClassLoader cl = context.getClassLoader();
                Class<?> miuiBlendTokenClass = XposedHelpers.findClassIfExists("miui.systemui.util.MiuiColorBlendToken", cl);
                if (miuiBlendTokenClass != null) {
                    Object tokenInstance = XposedHelpers.getStaticObjectField(miuiBlendTokenClass, "INSTANCE");
                    Object defaultBlend = XposedHelpers.callMethod(tokenInstance, "getCC_VOLUME_SLIDER_ICON_BLEND_COLORS_DEFAULT");
                    Object highlightBlend = XposedHelpers.callMethod(tokenInstance, "getCC_VOLUME_SLIDER_ICON_BLEND_COLORS");

                    if (volume == 0) {
                        XposedHelpers.callMethod(
                            iconView,
                            "updateIconColor",
                            highlightBlend,
                            defaultBlend,
                            volumeColorRes,
                            normalColorRes,
                            false
                        );
                    } else {
                        XposedHelpers.callMethod(
                            iconView,
                            "updateIconColor",
                            defaultBlend,
                            highlightBlend,
                            normalColorRes,
                            volumeColorRes,
                            false
                        );
                    }
                    blendApplied = true;
                }
            } catch (Throwable t) {
                Log.w(TAG, "Reflection updateIconColor failed: " + t.getMessage());
            }

            // 5. Fallback tint if blend token is not present or failed
            if (!blendApplied) {
                applyFallbackTint(iconView, res, volume, normalColorRes, volumeColorRes);
            }

            iconView.invalidate();
        } catch (Throwable t) {
            Log.e(TAG, "Error in updateIcon: " + t.getMessage(), t);
        }
    }

    private static void applyFallbackTint(View iconView, Resources res, int volume, int normalColorRes, int volumeColorRes) {
        try {
            if (!(iconView instanceof ImageView)) return;
            ImageView iv = (ImageView) iconView;
            Drawable d = iv.getDrawable();
            if (d == null) return;

            int colorRes = (volume == 0) ? normalColorRes : volumeColorRes;
            int color;
            if (res != null) {
                try {
                    color = res.getColor(colorRes, iconView.getContext().getTheme());
                } catch (Throwable ignored) {
                    color = (volume == 0) ? 0xFF959595 : 0xFF3482FF;
                }
            } else {
                color = (volume == 0) ? 0xFF959595 : 0xFF3482FF;
            }

            d.mutate().setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
        } catch (Throwable ignored) {}
    }

    private static void syncFromSystem(SeekBar slider, View iconView) {
        if (sAudioManager == null) return;
        try {
            int currentVol = sAudioManager.getStreamVolume(AudioManager.STREAM_RING);
            if (slider != null && !sIsDragging) {
                slider.setProgress(currentVol * 100);
            }
            if (iconView != null) {
                updateIcon(iconView, currentVol);
            }
        } catch (Throwable ignored) {}
    }

    private static synchronized void registerReceiver() {
        if (sVolumeReceiver != null || sContext == null) return;
        try {
            sVolumeReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (sMainHandler != null) {
                        sMainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                syncFromSystem(sCurrentSlider, sCurrentIconView);
                            }
                        });
                    }
                }
            };
            IntentFilter filter = new IntentFilter();
            filter.addAction("android.media.VOLUME_CHANGED_ACTION");
            filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
            sContext.registerReceiver(sVolumeReceiver, filter);
            Log.d(TAG, "Volume broadcast receiver registered.");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to register volume receiver: " + t.getMessage());
        }
    }

    private static synchronized void onViewAttached() {
        registerReceiver();
        syncFromSystem(sCurrentSlider, sCurrentIconView);
    }

    private static synchronized void onViewDetached() {
        if (sVolumeReceiver != null && sContext != null) {
            try {
                sContext.unregisterReceiver(sVolumeReceiver);
            } catch (Throwable ignored) {}
            sVolumeReceiver = null;
        }
        sCurrentSlider = null;
        sCurrentIconView = null;
        sCurrentHolder = null;
    }
}
