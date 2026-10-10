package com.hyperos.statusbar.expand;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class StatusBarExpandHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.android.systemui".equals(lpparam.packageName)) {
            return;
        }

        Logger.log("==================================================");
        Logger.log("HyperOS StatusBar Expand Module initialized!");
        Logger.log("Target package: " + lpparam.packageName + ", Process: " + lpparam.processName);
        Logger.log("Unlimited status icons mode active");
        Logger.log("==================================================");

        hookMiuiIconManagerUtils(lpparam.classLoader);
        hookMiuiStatusIconContainer(lpparam.classLoader);
        hookMiuiDripLeftStatusIconContainer(lpparam.classLoader);
        hookAospStatusIconContainer(lpparam.classLoader);
        hookMiuiPhoneStatusBarView(lpparam.classLoader);
    }

    private void hookMiuiIconManagerUtils(ClassLoader classLoader) {
        try {
            Class<?> utilsClass = XposedHelpers.findClassIfExists(
                    "com.android.systemui.statusbar.phone.MiuiIconManagerUtils", classLoader);
            if (utilsClass == null) {
                Logger.log("MiuiIconManagerUtils class not found in classloader");
                return;
            }

            clearBlockLists(utilsClass);

            XposedBridge.hookAllMethods(utilsClass, "<clinit>", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Logger.log("MiuiIconManagerUtils <clinit> executed, clearing block lists again");
                    clearBlockLists(utilsClass);
                }
            });

            Logger.log("Hooked MiuiIconManagerUtils successfully");
        } catch (Throwable t) {
            Logger.log("Failed to hook MiuiIconManagerUtils", t);
        }
    }

    private void clearBlockLists(Class<?> utilsClass) {
        String[] fieldNames = new String[]{
                "RIGHT_BLOCK_LIST",
                "MINI_RIGHT_BLOCK_LIST",
                "CONTROL_CENTER_BLOCK_LIST"
        };
        for (String fn : fieldNames) {
            try {
                Field f = XposedHelpers.findFieldIfExists(utilsClass, fn);
                if (f != null) {
                    f.setAccessible(true);
                    Object list = f.get(null);
                    if (list instanceof List) {
                        int originalSize = ((List<?>) list).size();
                        ((List<?>) list).clear();
                        Logger.log("Cleared " + fn + " (was " + originalSize + " items)");
                    }
                }
            } catch (Throwable t) {
                Logger.log("Failed to clear " + fn, t);
            }
        }

        try {
            for (Field f : utilsClass.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) && List.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    Object list = f.get(null);
                    if (list instanceof List) {
                        ((List<?>) list).clear();
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private void hookMiuiStatusIconContainer(ClassLoader classLoader) {
        try {
            Class<?> containerClass = XposedHelpers.findClassIfExists(
                    "com.android.systemui.statusbar.views.MiuiStatusIconContainer", classLoader);
            if (containerClass == null) {
                Logger.log("MiuiStatusIconContainer class not found in classloader");
                return;
            }

            // Hook constructors
            XposedBridge.hookAllConstructors(containerClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        XposedHelpers.setBooleanField(param.thisObject, "mShouldRestrictIcons", false);
                        XposedHelpers.setBooleanField(param.thisObject, "mNeedLimitIcon", false);
                        Logger.log("MiuiStatusIconContainer.<init>: restrictions initialized to false");
                    } catch (Throwable t) {
                        Logger.log("Error in MiuiStatusIconContainer.<init>", t);
                    }
                }
            });

            // Hook setIgnoredSlots
            XposedBridge.hookAllMethods(containerClass, "setIgnoredSlots", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        param.args[0] = new ArrayList<String>();
                    } catch (Throwable t) {
                        Logger.log("Error in setIgnoredSlots hook", t);
                    }
                }
            });

            // Force shouldRestrictIcons to false
            XposedBridge.hookAllMethods(containerClass, "setShouldRestrictIcons", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args != null && param.args.length > 0) {
                        param.args[0] = false;
                    }
                }
            });

            // Force needLimitIcon to false
            XposedBridge.hookAllMethods(containerClass, "setNeedLimitIcon", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args != null && param.args.length > 0) {
                        param.args[0] = false;
                    }
                }
            });

            // Hook onMeasure
            XposedBridge.hookAllMethods(containerClass, "onMeasure", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        ViewGroup container = (ViewGroup) param.thisObject;
                        XposedHelpers.setBooleanField(container, "mShouldRestrictIcons", false);
                        XposedHelpers.setBooleanField(container, "mNeedLimitIcon", false);
                        XposedHelpers.setIntField(container, "mIconSpacing", 3);

                        int widthSpec = (int) param.args[0];
                        int specMode = View.MeasureSpec.getMode(widthSpec);
                        int specSize = View.MeasureSpec.getSize(widthSpec);

                        if (specMode != View.MeasureSpec.EXACTLY || specSize < 1000) {
                            param.args[0] = View.MeasureSpec.makeMeasureSpec(4096, View.MeasureSpec.AT_MOST);
                        }
                    } catch (Throwable t) {
                        Logger.log("Error in MiuiStatusIconContainer.onMeasure before", t);
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        ViewGroup container = (ViewGroup) param.thisObject;
                        int measuredW = container.getMeasuredWidth();
                        int measuredH = container.getMeasuredHeight();
                        int count = container.getChildCount();
                        Logger.log("MiuiStatusIconContainer.onMeasure after: measured=(" + measuredW + "x" + measuredH + "), childCount=" + count);
                    } catch (Throwable ignored) {}
                }
            });

            // Hook onLayout
            XposedBridge.hookAllMethods(containerClass, "onLayout", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        XposedHelpers.setBooleanField(param.thisObject, "mShouldRestrictIcons", false);
                        XposedHelpers.setBooleanField(param.thisObject, "mNeedLimitIcon", false);
                    } catch (Throwable ignored) {}
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        ViewGroup container = (ViewGroup) param.thisObject;
                        layoutStatusIcons(container);
                    } catch (Throwable t) {
                        Logger.log("Error in MiuiStatusIconContainer.onLayout after", t);
                    }
                }
            });

            Logger.log("Hooked MiuiStatusIconContainer successfully");
        } catch (Throwable t) {
            Logger.log("Failed to hook MiuiStatusIconContainer", t);
        }
    }

    private void hookMiuiDripLeftStatusIconContainer(ClassLoader classLoader) {
        try {
            Class<?> dripClass = XposedHelpers.findClassIfExists(
                    "com.android.systemui.statusbar.views.MiuiDripLeftStatusIconContainer", classLoader);
            if (dripClass == null) return;

            XposedBridge.hookAllMethods(dripClass, "setIgnoredSlots", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    param.args[0] = new ArrayList<String>();
                }
            });

            XposedBridge.hookAllMethods(dripClass, "setShouldRestrictIcons", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args != null && param.args.length > 0) {
                        param.args[0] = false;
                    }
                }
            });

            XposedBridge.hookAllMethods(dripClass, "setNeedLimitIcon", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args != null && param.args.length > 0) {
                        param.args[0] = false;
                    }
                }
            });

            Logger.log("Hooked MiuiDripLeftStatusIconContainer successfully");
        } catch (Throwable t) {
            Logger.log("Failed to hook MiuiDripLeftStatusIconContainer", t);
        }
    }

    private void layoutStatusIcons(ViewGroup container) {
        int childCount = container.getChildCount();
        int containerWidth = container.getWidth();
        int paddingEnd = container.getPaddingEnd();
        int paddingStart = container.getPaddingStart();

        // 1. Resolve view state tag ID (status_bar_view_state_tag)
        int tagId = 0;
        try {
            tagId = container.getResources().getIdentifier("status_bar_view_state_tag", "id", "com.android.systemui");
        } catch (Throwable ignored) {}
        if (tagId == 0) {
            tagId = 0x7f0a0a38;
        }

        // 2. Disable clipping on container and parent hierarchy
        try {
            container.setClipChildren(false);
            container.setClipToPadding(false);
            ViewParent p = container.getParent();
            while (p instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) p;
                vg.setClipChildren(false);
                vg.setClipToPadding(false);
                p = vg.getParent();
            }
        } catch (Throwable ignored) {}

        // 3. Collect eligible displayable icons
        List<View> eligibleChildren = new ArrayList<>();
        for (int i = 0; i < childCount; i++) {
            View child = container.getChildAt(i);
            if (child == null) continue;

            boolean isBlocked = false;
            try {
                Object blockedObj = XposedHelpers.callMethod(child, "isIconBlocked");
                if (blockedObj instanceof Boolean) {
                    isBlocked = (Boolean) blockedObj;
                }
            } catch (Throwable ignored) {}

            boolean isVisible = true;
            try {
                Object visObj = XposedHelpers.callMethod(child, "isIconVisible");
                if (visObj instanceof Boolean) {
                    isVisible = (Boolean) visObj;
                }
            } catch (Throwable ignored) {}

            String slot = "unknown";
            try {
                Object slotObj = XposedHelpers.callMethod(child, "getSlot");
                if (slotObj != null) {
                    slot = slotObj.toString();
                }
            } catch (Throwable ignored) {}

            if (!isBlocked && isVisible) {
                eligibleChildren.add(child);
            } else {
                child.setVisibility(View.GONE);
                try {
                    XposedHelpers.callMethod(child, "setVisibleState", 2, false);
                } catch (Throwable ignored) {}
                Object tag = getIconState(child, tagId);
                if (tag != null) {
                    try {
                        XposedHelpers.setIntField(tag, "visibleState", 2);
                    } catch (Throwable ignored) {}
                }
            }
        }

        int count = eligibleChildren.size();
        Logger.log("MiuiStatusIconContainer.onLayout: containerWidth=" + containerWidth +
                ", childCount=" + childCount + ", eligible=" + count);

        if (count == 0) {
            return;
        }

        // 4. Calculate total width of all eligible icons
        int totalIconsWidth = 0;
        for (int i = 0; i < count; i++) {
            View child = eligibleChildren.get(i);
            int w = child.getWidth();
            if (w <= 0) w = child.getMeasuredWidth();
            if (w <= 0) w = 32;
            totalIconsWidth += w;
        }

        int availableWidth = Math.max(100, containerWidth - paddingStart - paddingEnd);
        int spacing = 3;
        if (count > 1) {
            int remainingSpace = availableWidth - totalIconsWidth;
            if (remainingSpace > 0) {
                spacing = Math.min(6, Math.max(2, remainingSpace / (count - 1)));
            } else {
                int needed = totalIconsWidth - availableWidth;
                int reduce = needed / (count - 1);
                spacing = Math.max(-2, 1 - reduce);
            }
        }

        // 5. Place ALL eligible icons right-to-left without any cap (genuinely unlimited)
        float currentX = containerWidth - paddingEnd;

        for (int i = count - 1; i >= 0; i--) {
            View child = eligibleChildren.get(i);
            int childW = child.getWidth();
            if (childW <= 0) childW = child.getMeasuredWidth();
            if (childW <= 0) childW = 32;

            currentX -= childW;
            float posX = currentX;

            child.setVisibility(View.VISIBLE);
            child.setAlpha(1.0f);
            child.setTranslationX(posX);

            try {
                XposedHelpers.callMethod(child, "setVisibleState", 0, false);
            } catch (Throwable ignored) {}

            Object tag = getIconState(child, tagId);
            if (tag != null) {
                try {
                    XposedHelpers.setIntField(tag, "visibleState", 0);
                    XposedHelpers.setFloatField(tag, "xTranslation", posX);
                    XposedHelpers.setFloatField(tag, "alpha", 1.0f);
                } catch (Throwable ignored) {}
            }

            currentX -= spacing;
        }
    }

    private Object getIconState(View child, int tagId) {
        if (child == null) return null;
        Object tag = null;
        if (tagId != 0) {
            try {
                tag = child.getTag(tagId);
            } catch (Throwable ignored) {}
        }
        if (tag == null) {
            try {
                tag = child.getTag();
            } catch (Throwable ignored) {}
        }
        return tag;
    }

    private void hookAospStatusIconContainer(ClassLoader classLoader) {
        try {
            Class<?> aospClass = XposedHelpers.findClassIfExists(
                    "com.android.systemui.statusbar.phone.StatusIconContainer", classLoader);
            if (aospClass == null) {
                Logger.log("AOSP StatusIconContainer class not found in classloader");
                return;
            }

            XposedBridge.hookAllConstructors(aospClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        XposedHelpers.setBooleanField(param.thisObject, "mShouldRestrictIcons", false);
                    } catch (Throwable ignored) {}
                }
            });

            XposedBridge.hookAllMethods(aospClass, "setShouldRestrictIcons", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args != null && param.args.length > 0) {
                        param.args[0] = false;
                    }
                }
            });

            XposedBridge.hookAllMethods(aospClass, "onMeasure", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        XposedHelpers.setBooleanField(param.thisObject, "mShouldRestrictIcons", false);
                    } catch (Throwable ignored) {}
                }
            });

            XposedBridge.hookAllMethods(aospClass, "onLayout", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        XposedHelpers.setBooleanField(param.thisObject, "mShouldRestrictIcons", false);
                    } catch (Throwable ignored) {}
                }
            });

            Logger.log("Hooked AOSP StatusIconContainer successfully");
        } catch (Throwable t) {
            Logger.log("Failed to hook AOSP StatusIconContainer", t);
        }
    }

    private void hookMiuiPhoneStatusBarView(ClassLoader classLoader) {
        try {
            Class<?> statusBarViewClass = XposedHelpers.findClassIfExists(
                    "com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView", classLoader);
            if (statusBarViewClass == null) {
                Logger.log("MiuiPhoneStatusBarView class not found in classloader");
                return;
            }

            XposedBridge.hookAllMethods(statusBarViewClass, "updateCutoutLocation", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (param.thisObject instanceof ViewGroup) {
                            ((ViewGroup) param.thisObject).setClipChildren(false);
                            ((ViewGroup) param.thisObject).setClipToPadding(false);
                        }

                        Object statusIcons = XposedHelpers.getObjectField(param.thisObject, "mStatusBarStatusIcons");
                        if (statusIcons != null) {
                            XposedHelpers.callMethod(statusIcons, "setIgnoredSlots", new ArrayList<String>());
                            XposedHelpers.setBooleanField(statusIcons, "mShouldRestrictIcons", false);
                            XposedHelpers.setBooleanField(statusIcons, "mNeedLimitIcon", false);
                            Logger.log("MiuiPhoneStatusBarView.updateCutoutLocation: cleared ignored slots on mStatusBarStatusIcons");
                        }

                        Object leftContainer = XposedHelpers.getObjectField(param.thisObject, "mStatusBarLeftContainer");
                        Object systemIconArea = XposedHelpers.getObjectField(param.thisObject, "mSystemIconArea");

                        if (leftContainer instanceof View && systemIconArea instanceof View) {
                            View leftView = (View) leftContainer;
                            View rightView = (View) systemIconArea;

                            ViewGroup.LayoutParams leftLp = leftView.getLayoutParams();
                            ViewGroup.LayoutParams rightLp = rightView.getLayoutParams();

                            if (leftLp instanceof LinearLayout.LayoutParams && rightLp instanceof LinearLayout.LayoutParams) {
                                LinearLayout.LayoutParams rightLinLp = (LinearLayout.LayoutParams) rightLp;
                                LinearLayout.LayoutParams leftLinLp = (LinearLayout.LayoutParams) leftLp;

                                leftLinLp.weight = 0.7f;
                                rightLinLp.weight = 1.3f;

                                leftView.setLayoutParams(leftLinLp);
                                rightView.setLayoutParams(rightLinLp);
                                Logger.log("Containers adjusted weights: left.weight=0.7, right.weight=1.3");
                            }
                        }
                    } catch (Throwable t) {
                        Logger.log("Error in updateCutoutLocation after", t);
                    }
                }
            });

            Logger.log("Hooked MiuiPhoneStatusBarView successfully");
        } catch (Throwable t) {
            Logger.log("Failed to hook MiuiPhoneStatusBarView", t);
        }
    }
}
