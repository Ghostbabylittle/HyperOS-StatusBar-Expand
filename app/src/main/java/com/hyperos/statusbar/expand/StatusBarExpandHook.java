package com.hyperos.statusbar.expand;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class StatusBarExpandHook implements IXposedHookLoadPackage {

    private static final int MAX_STATUS_ICONS = 12;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.android.systemui".equals(lpparam.packageName)) {
            return;
        }

        Logger.log("==================================================");
        Logger.log("HyperOS StatusBar Expand Module initialized!");
        Logger.log("Target package: " + lpparam.packageName + ", Process: " + lpparam.processName);
        Logger.log("Max status icons cap: " + MAX_STATUS_ICONS);
        Logger.log("==================================================");

        hookMiuiIconManagerUtils(lpparam.classLoader);
        hookMiuiStatusIconContainer(lpparam.classLoader);
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
        try {
            Field rightBlockField = XposedHelpers.findFieldIfExists(utilsClass, "RIGHT_BLOCK_LIST");
            if (rightBlockField != null) {
                Object list = rightBlockField.get(null);
                if (list instanceof List) {
                    int originalSize = ((List<?>) list).size();
                    ((List<?>) list).clear();
                    Logger.log("Cleared RIGHT_BLOCK_LIST (was " + originalSize + " items)");
                }
            }

            Field miniRightBlockField = XposedHelpers.findFieldIfExists(utilsClass, "MINI_RIGHT_BLOCK_LIST");
            if (miniRightBlockField != null) {
                Object list = miniRightBlockField.get(null);
                if (list instanceof List) {
                    int originalSize = ((List<?>) list).size();
                    ((List<?>) list).clear();
                    Logger.log("Cleared MINI_RIGHT_BLOCK_LIST (was " + originalSize + " items)");
                }
            }
        } catch (Throwable t) {
            Logger.log("Failed to clear block lists", t);
        }
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
                        if (param.args != null && param.args.length > 0 && param.args[0] instanceof List) {
                            List<?> requested = (List<?>) param.args[0];
                            Logger.log("MiuiStatusIconContainer.setIgnoredSlots called with " + requested.size() + " items: " + requested);
                            param.args[0] = new ArrayList<String>();
                            Logger.log("MiuiStatusIconContainer.setIgnoredSlots overridden with empty list");
                        }
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
                        Logger.log("MiuiStatusIconContainer.setShouldRestrictIcons(" + param.args[0] + ") -> forcing false");
                        param.args[0] = false;
                    }
                }
            });

            // Force needLimitIcon to false
            XposedBridge.hookAllMethods(containerClass, "setNeedLimitIcon", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args != null && param.args.length > 0) {
                        Logger.log("MiuiStatusIconContainer.setNeedLimitIcon(" + param.args[0] + ") -> forcing false");
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

                        int childCount = container.getChildCount();
                        int widthSpec = (int) param.args[0];
                        int specMode = View.MeasureSpec.getMode(widthSpec);
                        int specSize = View.MeasureSpec.getSize(widthSpec);

                        // If icon count is high, condense spacing so up to 12 fit within the right half of the screen
                        if (childCount > 6) {
                            try {
                                XposedHelpers.setIntField(container, "mIconSpacing", 3);
                            } catch (Throwable ignored) {}
                        }

                        // Ensure available width is large enough (at least 700px) so onMeasure doesn't stop measuring early
                        if (specMode == View.MeasureSpec.AT_MOST && specSize < 700) {
                            param.args[0] = View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.AT_MOST);
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

            // Hook onLayout: completely layout up to 12 icons with proper positive coordinates
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
                        int childCount = container.getChildCount();
                        int containerWidth = container.getWidth();
                        int paddingEnd = container.getPaddingEnd();

                        Logger.log("MiuiStatusIconContainer.onLayout after: containerWidth=" + containerWidth + ", childCount=" + childCount);

                        // Collect valid displayable children
                        List<View> visibleChildren = new ArrayList<>();
                        for (int i = 0; i < childCount; i++) {
                            View child = container.getChildAt(i);
                            if (child == null) continue;

                            Object tag = child.getTag();
                            if (tag == null) continue;

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

                            Logger.log("  Child " + i + ": slot=" + slot + ", isVisible=" + isVisible + ", isBlocked=" + isBlocked + ", width=" + child.getWidth());

                            if (!isBlocked && isVisible) {
                                visibleChildren.add(child);
                            } else {
                                child.setVisibility(View.GONE);
                            }
                        }

                        int totalToDisplay = Math.min(visibleChildren.size(), MAX_STATUS_ICONS);
                        Logger.log("Visible status icons eligible: " + visibleChildren.size() + ", displaying: " + totalToDisplay);

                        // Calculate dynamic spacing so all icons fit cleanly with positive xTranslation
                        int totalIconsWidth = 0;
                        for (int i = 0; i < totalToDisplay; i++) {
                            View v = visibleChildren.get(i);
                            int w = v.getWidth();
                            if (w <= 0) w = v.getMeasuredWidth();
                            if (w <= 0) w = 32;
                            totalIconsWidth += w;
                        }

                        int availableWidth = Math.max(100, containerWidth - container.getPaddingStart() - paddingEnd);
                        int spacing = 3;
                        if (totalToDisplay > 1) {
                            int remainingSpace = availableWidth - totalIconsWidth;
                            if (remainingSpace > 0) {
                                spacing = Math.min(6, remainingSpace / (totalToDisplay - 1));
                            } else {
                                spacing = 1;
                            }
                        }

                        int currentX = containerWidth - paddingEnd;

                        for (int i = 0; i < visibleChildren.size(); i++) {
                            View child = visibleChildren.get(i);
                            Object tag = child.getTag();

                            if (i < totalToDisplay) {
                                int childW = child.getWidth();
                                if (childW <= 0) {
                                    childW = child.getMeasuredWidth();
                                }
                                if (childW <= 0) {
                                    childW = 32;
                                }

                                currentX -= childW;
                                int posX = Math.max(0, currentX);

                                child.setVisibility(View.VISIBLE);
                                child.setAlpha(1.0f);
                                child.setTranslationX(posX);

                                try {
                                    XposedHelpers.setIntField(tag, "visibleState", 0); // VISIBLE
                                    XposedHelpers.setFloatField(tag, "xTranslation", (float) posX);
                                    XposedHelpers.setFloatField(tag, "alpha", 1.0f);
                                } catch (Throwable ignored) {}

                                currentX -= spacing;
                            } else {
                                // Cap beyond MAX_STATUS_ICONS (12)
                                child.setVisibility(View.GONE);
                                try {
                                    XposedHelpers.setIntField(tag, "visibleState", 2); // GONE
                                } catch (Throwable ignored) {}
                            }
                        }
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
                        Object statusIcons = XposedHelpers.getObjectField(param.thisObject, "mStatusBarStatusIcons");
                        if (statusIcons != null) {
                            XposedHelpers.callMethod(statusIcons, "setIgnoredSlots", new ArrayList<String>());
                            XposedHelpers.setBooleanField(statusIcons, "mShouldRestrictIcons", false);
                            XposedHelpers.setBooleanField(statusIcons, "mNeedLimitIcon", false);
                            Logger.log("MiuiPhoneStatusBarView.updateCutoutLocation: cleared ignored slots on mStatusBarStatusIcons");
                        }

                        // Inspect and adjust layout params of left container vs right system icon area
                        Object leftContainer = XposedHelpers.getObjectField(param.thisObject, "mStatusBarLeftContainer");
                        Object systemIconArea = XposedHelpers.getObjectField(param.thisObject, "mSystemIconArea");

                        if (leftContainer instanceof View && systemIconArea instanceof View) {
                            View leftView = (View) leftContainer;
                            View rightView = (View) systemIconArea;

                            ViewGroup.LayoutParams leftLp = leftView.getLayoutParams();
                            ViewGroup.LayoutParams rightLp = rightView.getLayoutParams();

                            Logger.log("Containers before: left=(w=" + leftLp.width + "), right=(w=" + rightLp.width + ")");

                            // If right container has weight 1.0f, give it 1.2f so right side can allocate more horizontal space
                            if (leftLp instanceof LinearLayout.LayoutParams && rightLp instanceof LinearLayout.LayoutParams) {
                                LinearLayout.LayoutParams rightLinLp = (LinearLayout.LayoutParams) rightLp;
                                LinearLayout.LayoutParams leftLinLp = (LinearLayout.LayoutParams) leftLp;

                                leftLinLp.weight = 0.8f;
                                rightLinLp.weight = 1.2f;

                                leftView.setLayoutParams(leftLinLp);
                                rightView.setLayoutParams(rightLinLp);
                                Logger.log("Containers adjusted weights: left.weight=0.8, right.weight=1.2");
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
