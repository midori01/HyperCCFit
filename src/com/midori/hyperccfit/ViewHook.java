package com.midori.hyperccfit;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ViewHook {
    private static final String TAG = "HyperCCFit_ViewHook";
    public static final int GRID_SPAN_COUNT = 30;
    public static final int TAG_MARQUEE_KEY = 0x7f09cc01;

    private static boolean sHooked = false;

    public static void initHook(ClassLoader classLoader) {
        if (sHooked) return;
        sHooked = true;

        hookMainPanelController(classLoader);
        hookControlCenterWindowView(classLoader);
        hookMediaPlayerViewHolder(classLoader);
        hookMainPanelContentDistributor(classLoader);
        hookMainPanelAdapter(classLoader);
        hookToggleSlider(classLoader);
        hookQSTileItemView(classLoader);
        hookViewFocusForMarquee(classLoader);
    }

    private static void hookMainPanelController(ClassLoader classLoader) {
        try {
            Class<?> mainPanelControllerClass = XposedHelpers.findClass(
                "miui.systemui.controlcenter.panel.main.MainPanelController",
                classLoader
            );

            XposedHelpers.findAndHookMethod(
                mainPanelControllerClass,
                "updatePanelWidth",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            if (!ConfigManager.isEnabled()) return;

                            Object controller = param.thisObject;
                            Context context = (Context) XposedHelpers.callMethod(controller, "getContext");
                            if (context == null) return;

                            Resources res = context.getResources();
                            Configuration config = res.getConfiguration();
                            if (config == null || config.orientation != Configuration.ORIENTATION_PORTRAIT) {
                                return;
                            }

                            int targetWidthPx = CCFitCalculator.getTargetPanelWidthPx(res);
                            if (targetWidthPx > 0) {
                                XposedHelpers.setIntField(controller, "panelWidth", targetWidthPx);
                                Log.d(TAG, "MainPanelController.updatePanelWidth forced to: " + targetWidthPx);
                            }
                        } catch (Throwable t) {
                            Log.e(TAG, "Error in updatePanelWidth hook", t);
                        }
                    }
                }
            );

            XposedHelpers.findAndHookMethod(
                mainPanelControllerClass,
                "updatePanelSize",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            if (!ConfigManager.isEnabled()) return;

                            Object controller = param.thisObject;
                            Context context = (Context) XposedHelpers.callMethod(controller, "getContext");
                            if (context == null) return;

                            Resources res = context.getResources();
                            Configuration config = res.getConfiguration();
                            if (config == null || config.orientation != Configuration.ORIENTATION_PORTRAIT) {
                                return;
                            }

                            int targetWidthPx = CCFitCalculator.getTargetPanelWidthPx(res);
                            if (targetWidthPx <= 0) return;

                            View rightMainPanel = (View) XposedHelpers.getObjectField(controller, "rightMainPanel");
                            if (rightMainPanel != null) {
                                ViewGroup.LayoutParams lp = rightMainPanel.getLayoutParams();
                                if (lp != null && lp.width != targetWidthPx) {
                                    lp.width = targetWidthPx;
                                    rightMainPanel.setLayoutParams(lp);
                                    Log.d(TAG, "rightMainPanel layout width enforced to: " + targetWidthPx);
                                }
                            }
                        } catch (Throwable t) {
                            Log.e(TAG, "Error in updatePanelSize hook", t);
                        }
                    }
                }
            );

            Log.i(TAG, "Successfully hooked MainPanelController.");
        } catch (Throwable t) {
            Log.w(TAG, "MainPanelController not found in current ClassLoader: " + t.getMessage());
        }
    }

    private static void hookControlCenterWindowView(ClassLoader classLoader) {
        try {
            Class<?> windowViewClass = XposedHelpers.findClass(
                "miui.systemui.controlcenter.windowview.ControlCenterWindowViewImpl",
                classLoader
            );

            XposedHelpers.findAndHookMethod(
                windowViewClass,
                "onMeasure",
                int.class,
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            if (!ConfigManager.isEnabled()) return;

                            View windowView = (View) param.thisObject;
                            Context context = windowView.getContext();
                            if (context == null) return;

                            Resources res = context.getResources();
                            Configuration config = res.getConfiguration();
                            if (config == null || config.orientation != Configuration.ORIENTATION_PORTRAIT) {
                                return;
                            }

                            int targetWidthPx = CCFitCalculator.getTargetPanelWidthPx(res);
                            if (targetWidthPx <= 0) return;

                            Object windowViewController = XposedHelpers.callMethod(windowView, "getWindowViewController");
                            if (windowViewController != null) {
                                Object mainPanelController = XposedHelpers.callMethod(windowViewController, "getMainPanelController");
                                if (mainPanelController != null) {
                                    int currentWidth = XposedHelpers.getIntField(mainPanelController, "panelWidth");
                                    if (currentWidth != targetWidthPx) {
                                        XposedHelpers.setIntField(mainPanelController, "panelWidth", targetWidthPx);
                                        View rightMainPanel = (View) XposedHelpers.getObjectField(mainPanelController, "rightMainPanel");
                                        if (rightMainPanel != null && rightMainPanel.getLayoutParams() != null) {
                                            rightMainPanel.getLayoutParams().width = targetWidthPx;
                                        }
                                    }
                                }
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
            );

            Log.i(TAG, "Successfully hooked ControlCenterWindowViewImpl.onMeasure.");
        } catch (Throwable t) {
            Log.w(TAG, "ControlCenterWindowViewImpl not found in current ClassLoader: " + t.getMessage());
        }
    }

    /**
     * Widen media player to MATCH_PARENT, perfectly aligning its right edge with Wi-Fi card above.
     */
    private static void hookMediaPlayerViewHolder(ClassLoader classLoader) {
        try {
            Class<?> mediaPlayerViewHolderClass = XposedHelpers.findClassIfExists(
                "miui.systemui.controlcenter.panel.main.media.MediaPlayerController$MediaPlayerViewHolder",
                classLoader
            );
            if (mediaPlayerViewHolderClass != null) {
                XposedBridge.hookAllConstructors(mediaPlayerViewHolderClass, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            if (!ConfigManager.isEnabled()) return;
                            enforceMediaMatchParent(param.thisObject);
                        } catch (Throwable ignored) {}
                    }
                });

                XposedHelpers.findAndHookMethod(
                    mediaPlayerViewHolderClass,
                    "updateSize",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                if (!ConfigManager.isEnabled()) return;
                                enforceMediaMatchParent(param.thisObject);
                            } catch (Throwable ignored) {}
                        }
                    }
                );
            }

            Class<?> mediaPlayerPanelClass = XposedHelpers.findClassIfExists(
                "miui.systemui.controlcenter.panel.main.media.MediaPlayerPanel",
                classLoader
            );
            if (mediaPlayerPanelClass != null) {
                XposedHelpers.findAndHookMethod(
                    mediaPlayerPanelClass,
                    "onMeasure",
                    int.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (!ConfigManager.isEnabled()) return;
                                View view = (View) param.thisObject;
                                ViewGroup.LayoutParams lp = view.getLayoutParams();
                                if (lp != null) {
                                    boolean changed = false;
                                    if (lp.width != ViewGroup.LayoutParams.MATCH_PARENT) {
                                        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                                        changed = true;
                                    }
                                    int targetHeightPx = CCFitCalculator.calculateDimensionPx(
                                        CCFitCalculator.DIMEN_2_ROWS,
                                        view.getResources()
                                    );
                                    if (targetHeightPx > 0 && lp.height != targetHeightPx) {
                                        lp.height = targetHeightPx;
                                        changed = true;
                                    }
                                    if (changed) {
                                        view.setLayoutParams(lp);
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                );
            }

            Log.i(TAG, "Successfully hooked MediaPlayerViewHolder for full-width alignment.");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook MediaPlayerViewHolder: " + t.getMessage());
        }
    }

    private static void enforceMediaMatchParent(Object viewHolder) {
        if (viewHolder == null) return;
        try {
            Object binding = XposedHelpers.getObjectField(viewHolder, "binding");
            if (binding == null) return;
            View root = (View) XposedHelpers.callMethod(binding, "getRoot");
            if (root != null) {
                ViewGroup.LayoutParams lp = root.getLayoutParams();
                if (lp != null) {
                    boolean changed = false;
                    if (lp.width != ViewGroup.LayoutParams.MATCH_PARENT) {
                        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                        changed = true;
                    }
                    int targetHeightPx = CCFitCalculator.calculateDimensionPx(
                        CCFitCalculator.DIMEN_2_ROWS,
                        root.getResources()
                    );
                    if (targetHeightPx > 0 && lp.height != targetHeightPx) {
                        lp.height = targetHeightPx;
                        changed = true;
                    }
                    if (changed) {
                        root.setLayoutParams(lp);
                        Log.d(TAG, "enforceMediaMatchParent: MediaPlayerPanel width=MATCH_PARENT, height=" + targetHeightPx);
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "enforceMediaMatchParent error: " + t.getMessage());
        }
    }

    /**
     * Inject RingerSliderController immediately after VolumeSliderController in MainPanelContentDistributor.
     */
    private static void hookMainPanelContentDistributor(final ClassLoader classLoader) {
        try {
            Class<?> distributorClass = XposedHelpers.findClassIfExists(
                "miui.systemui.controlcenter.panel.main.MainPanelContentDistributor",
                classLoader
            );
            if (distributorClass == null) return;

            XposedBridge.hookAllConstructors(distributorClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object distributor = param.thisObject;
                        Context context = (Context) XposedHelpers.callMethod(distributor, "getContext");
                        ArrayList<?> childControllers = (ArrayList<?>) XposedHelpers.getObjectField(distributor, "childControllers");
                        if (childControllers != null) {
                            for (Object item : childControllers) {
                                if (item != null && item.getClass().getName().contains("VolumeSliderController")) {
                                    RingerSliderManager.setVolumeSliderController(item, context);
                                    Log.i(TAG, "Captured VolumeSliderController from distributor constructor.");
                                    break;
                                }
                            }
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "Error in distributor constructor hook: " + t.getMessage());
                    }
                }
            });

            XposedHelpers.findAndHookMethod(
                distributorClass,
                "distributePanels",
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            if (!ConfigManager.isEnabled() || !ConfigManager.isRingerSliderEnabled()) return;

                            boolean inHorizontal = (boolean) param.args[0];
                            if (inHorizontal) return; // Only add to rightPanelContent in vertical mode

                            Object distributor = param.thisObject;
                            ArrayList<?> rightPanelContent = (ArrayList<?>) XposedHelpers.getObjectField(
                                distributor,
                                "rightPanelContent"
                            );
                            if (rightPanelContent == null || rightPanelContent.isEmpty()) return;

                            Context context = (Context) XposedHelpers.callMethod(distributor, "getContext");
                            Object ringerController = RingerSliderManager.getOrCreateRingerController(classLoader);
                            if (ringerController == null) return;

                            Object volumeSliderController = null;
                            int volumeIndex = -1;
                            int ringerIndex = -1;

                            for (int i = 0; i < rightPanelContent.size(); i++) {
                                Object item = rightPanelContent.get(i);
                                if (item != null) {
                                    String className = item.getClass().getName();
                                    if (className.contains("VolumeSliderController")) {
                                        volumeSliderController = item;
                                        volumeIndex = i;
                                    }
                                    if (item == ringerController) {
                                        ringerIndex = i;
                                    }
                                }
                            }

                            if (volumeSliderController != null) {
                                RingerSliderManager.setVolumeSliderController(volumeSliderController, context);

                                @SuppressWarnings("unchecked")
                                ArrayList<Object> list = (ArrayList<Object>) rightPanelContent;
                                if (ringerIndex != -1) {
                                    list.remove(ringerIndex);
                                }
                                int newVolIndex = list.indexOf(volumeSliderController);
                                if (newVolIndex != -1) {
                                    list.add(newVolIndex + 1, ringerController);
                                    Log.i(TAG, "Inserted RingerSliderController at index " + (newVolIndex + 1));
                                }
                            }
                        } catch (Throwable t) {
                            Log.e(TAG, "Error in distributePanels hook", t);
                        }
                    }
                }
            );

            Log.i(TAG, "Successfully hooked MainPanelContentDistributor.");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook MainPanelContentDistributor: " + t.getMessage());
        }
    }

    /**
     * Reconfigure MainPanelAdapter to use 30 spans:
     * - Row 1: Wi-Fi (15) + Cellular (15) = 30 spans
     * - Row 2: Media (15) + Brightness (5) + Media Volume (5) + Ringer Volume (5) = 30 spans
     * - Row 3: DeviceCenter (30 spans)
     * - Quick Setting Tiles: 6 spans each -> 5 tiles per row (5 * 6 = 30 spans)
     * - Edit button: 30 spans
     */
    private static void hookMainPanelAdapter(final ClassLoader classLoader) {
        try {
            Class<?> mainPanelAdapterClass = XposedHelpers.findClassIfExists(
                "miui.systemui.controlcenter.panel.main.recyclerview.MainPanelAdapter",
                classLoader
            );
            if (mainPanelAdapterClass == null) return;

            // 1. Hook setSpan: Replace default 4/20/3 spans with 30 spans
            XposedHelpers.findAndHookMethod(
                mainPanelAdapterClass,
                "setSpan",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            if (!ConfigManager.isEnabled()) return;
                            param.args[0] = GRID_SPAN_COUNT;
                        } catch (Throwable ignored) {
                        }
                    }
                }
            );

            // 2. Hook onAttachedToRecyclerView: Keep layoutManager spanCount at 30
            XposedHelpers.findAndHookMethod(
                mainPanelAdapterClass,
                "onAttachedToRecyclerView",
                "androidx.recyclerview.widget.RecyclerView",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            if (!ConfigManager.isEnabled()) return;
                            Object adapter = param.thisObject;
                            XposedHelpers.setIntField(adapter, "span", GRID_SPAN_COUNT);
                            Object lm = XposedHelpers.getObjectField(adapter, "layoutManager");
                            if (lm != null) {
                                XposedHelpers.callMethod(lm, "setSpanCount", GRID_SPAN_COUNT);
                            }
                        } catch (Throwable t) {
                            Log.e(TAG, "Error in onAttachedToRecyclerView hook", t);
                        }
                    }
                }
            );

            // 3. Hook getSpanSize(int): Assign span proportions under 30 spans
            Class<?> spanLookupClass = XposedHelpers.findClassIfExists(
                "miui.systemui.controlcenter.panel.main.recyclerview.MainPanelAdapter$layoutManager$1$1",
                classLoader
            );
            if (spanLookupClass != null) {
                XposedHelpers.findAndHookMethod(
                    spanLookupClass,
                    "getSpanSize",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (!ConfigManager.isEnabled()) return;
                                int position = (int) param.args[0];
                                Object adapter = XposedHelpers.getObjectField(param.thisObject, "this$0");
                                if (adapter == null) return;
                                Map<?, ?> contentMap = (Map<?, ?>) XposedHelpers.getObjectField(adapter, "contentMap");
                                if (contentMap == null) return;

                                Object companion = XposedHelpers.getStaticObjectField(adapter.getClass(), "Companion");
                                Object item = XposedHelpers.callMethod(companion, "getItem", contentMap, position);
                                if (item == null) {
                                    param.setResult(GRID_SPAN_COUNT);
                                    return;
                                }

                                int type = (int) XposedHelpers.callMethod(item, "getType");
                                if (type == 2273) {
                                    // Wi-Fi or Cellular card: 15 / 30 spans (50%)
                                    param.setResult(15);
                                } else if (type == 63342) {
                                    // Media Player: 15 / 30 spans (50%)
                                    param.setResult(15);
                                } else if (type == 274442 || type == 865269 || type == RingerSliderManager.TYPE_RINGER_SLIDER) {
                                    // Brightness, Media Volume, Ringtone Volume: 5 / 30 spans each (16.67%)
                                    // 3 sliders together = 15 spans, filling the right half perfectly!
                                    param.setResult(5);
                                } else if (type == 8453) {
                                    // Circular Quick Setting Tiles: 6 / 30 spans each (20%) -> 5 tiles per row!
                                    param.setResult(6);
                                } else {
                                    // DeviceCenter, Edit button, dividers, etc: full width 30 spans
                                    param.setResult(GRID_SPAN_COUNT);
                                }
                            } catch (Throwable t) {
                                Log.e(TAG, "Error in getSpanSize hook", t);
                            }
                        }
                    }
                );
            }

            Log.i(TAG, "Successfully hooked MainPanelAdapter for 30-span 3-slider 5-column grid.");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook MainPanelAdapter: " + t.getMessage());
        }
    }

    /**
     * Adapt ToggleSliderViewHolder itemView and rotated seekbar to fill their 5-span cell elegantly.
     */
    private static void hookToggleSlider(ClassLoader classLoader) {
        try {
            Class<?> toggleSliderViewHolderClass = XposedHelpers.findClassIfExists(
                "miui.systemui.controlcenter.panel.main.recyclerview.ToggleSliderViewHolder",
                classLoader
            );
            if (toggleSliderViewHolderClass == null) return;

            XposedHelpers.findAndHookMethod(
                toggleSliderViewHolderClass,
                "updateSize",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            if (!ConfigManager.isEnabled()) return;

                            Object holder = param.thisObject;
                            View itemView = (View) XposedHelpers.getObjectField(holder, "itemView");
                            if (itemView == null) return;

                            Resources res = itemView.getResources();
                            Configuration config = res.getConfiguration();
                            if (config == null || config.orientation != Configuration.ORIENTATION_PORTRAIT) return;

                            int sliderHeightPx = CCFitCalculator.calculateDimensionPx(CCFitCalculator.DIMEN_2_ROWS, res);

                            ViewGroup.LayoutParams lp = itemView.getLayoutParams();
                            if (lp != null) {
                                lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                                if (sliderHeightPx > 0) {
                                    lp.height = sliderHeightPx;
                                }
                                itemView.setLayoutParams(lp);
                            }

                            Object binding = XposedHelpers.callMethod(holder, "getBinding");
                            if (binding != null) {
                                View slider = (View) XposedHelpers.getObjectField(binding, "slider");
                                if (slider != null) {
                                    ViewGroup.LayoutParams slp = slider.getLayoutParams();
                                    if (slp != null && sliderHeightPx > 0) {
                                        slp.width = sliderHeightPx;
                                        slp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                                        slider.setLayoutParams(slp);
                                    }
                                }
                            }
                        } catch (Throwable t) {
                            Log.e(TAG, "Error in ToggleSliderViewHolder.updateSize hook", t);
                        }
                    }
                }
            );

            Log.i(TAG, "Successfully hooked ToggleSliderViewHolder for slender sliders.");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook ToggleSliderViewHolder: " + t.getMessage());
        }
    }

    /**
     * Configure circular QS tile titles:
     * - Never wrap to multiple lines (single line only).
     * - Enable continuous loop marquee scrolling for long titles.
     * - Keep system native vertical spacing untouched (prevent text/icon overlapping).
     */
    private static void hookQSTileItemView(ClassLoader classLoader) {
        try {
            Class<?> qsTileItemViewClass = XposedHelpers.findClassIfExists(
                "miui.systemui.controlcenter.qs.tileview.QSTileItemView",
                classLoader
            );
            if (qsTileItemViewClass == null) return;

            XC_MethodHook marqueeHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (!ConfigManager.isEnabled()) return;
                        applyMarqueeToTileLabel(param.thisObject);
                    } catch (Throwable ignored) {}
                }
            };

            XposedHelpers.findAndHookMethod(qsTileItemViewClass, "onFinishInflate", marqueeHook);
            XposedHelpers.findAndHookMethod(qsTileItemViewClass, "updateSize", marqueeHook);
            XposedHelpers.findAndHookMethod(qsTileItemViewClass, "updateTextAppearance", marqueeHook);
            XposedHelpers.findAndHookMethod(qsTileItemViewClass, "onStateUpdated", boolean.class, marqueeHook);

            Class<?> qstileStateClass = XposedHelpers.findClassIfExists(
                "com.android.systemui.plugins.qs.QSTile$State",
                classLoader
            );
            if (qstileStateClass != null) {
                XposedHelpers.findAndHookMethod(
                    qsTileItemViewClass,
                    "updateState",
                    qstileStateClass,
                    boolean.class,
                    boolean.class,
                    marqueeHook
                );
                XposedHelpers.findAndHookMethod(
                    qsTileItemViewClass,
                    "updateCustomizeState",
                    qstileStateClass,
                    boolean.class,
                    marqueeHook
                );
            }

            Log.i(TAG, "Successfully hooked QSTileItemView for single-line marquee labels.");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook QSTileItemView: " + t.getMessage());
        }
    }

    private static void applyMarqueeToTileLabel(Object tileView) {
        if (tileView == null) return;
        try {
            Object binding = XposedHelpers.callMethod(tileView, "getBinding");
            if (binding == null) return;

            final TextView label = (TextView) XposedHelpers.getObjectField(binding, "tileLabel");
            if (label != null) {
                label.setSingleLine(true);
                label.setMaxLines(1);
                label.setEllipsize(TextUtils.TruncateAt.MARQUEE);
                label.setMarqueeRepeatLimit(-1);
                label.setHorizontallyScrolling(true);
                label.setFocusable(false);
                label.setFocusableInTouchMode(false);
                label.setTag(TAG_MARQUEE_KEY, Boolean.TRUE);
                label.setSelected(true);
                label.post(new Runnable() {
                    @Override
                    public void run() {
                        label.setSelected(true);
                    }
                });
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Intercept View focus/selection for marquee-tagged TextViews so marquee never pauses.
     */
    private static void hookViewFocusForMarquee(ClassLoader classLoader) {
        try {
            XC_MethodHook focusHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.thisObject instanceof TextView) {
                        View view = (View) param.thisObject;
                        if (Boolean.TRUE.equals(view.getTag(TAG_MARQUEE_KEY))) {
                            param.setResult(true);
                        }
                    }
                }
            };

            XposedHelpers.findAndHookMethod(View.class, "isFocused", focusHook);
            XposedHelpers.findAndHookMethod(View.class, "hasFocus", focusHook);
            XposedHelpers.findAndHookMethod(View.class, "isSelected", focusHook);
            Log.i(TAG, "Successfully hooked View focus/selection for marquee labels.");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook View focus/selection: " + t.getMessage());
        }
    }
}
