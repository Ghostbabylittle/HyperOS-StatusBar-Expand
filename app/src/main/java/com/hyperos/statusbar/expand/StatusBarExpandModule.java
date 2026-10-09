package com.hyperos.statusbar.expand;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

public class StatusBarExpandModule extends XposedModule {

    private static final int MAX_STATUS_ICONS = 12;

    public StatusBarExpandModule() {
        super();
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!param.isFirstPackage()) {
            return;
        }

        String packageName = param.getPackageName();
        Logger.log("StatusBarExpandModule.onPackageReady: package = " + packageName);

        if ("system".equals(packageName) || "android".equals(packageName)) {
            Logger.log("StatusBarExpandModule: system_server active");
            return;
        }

        if (!"com.android.systemui".equals(packageName)) {
            return;
        }

        Logger.log("==================================================");
        Logger.log("HyperOS StatusBar Expand Module (LibXposed API 101) loaded!");
        Logger.log("Target package: com.android.systemui");
        Logger.log("Max status icons cap: " + MAX_STATUS_ICONS);
        Logger.log("==================================================");

        ClassLoader cl = param.getClassLoader();
        hookMiuiIconManagerUtils(cl);
        hookMiuiStatusIconContainer(cl);
        hookAospStatusIconContainer(cl);
        hookMiuiPhoneStatusBarView(cl);
    }

    private void hookMiuiIconManagerUtils(ClassLoader cl) {
        try {
            Class<?> utilsClass = findClass(cl, "com.android.systemui.statusbar.phone.MiuiIconManagerUtils");
            if (utilsClass == null) {
                Logger.log("MiuiIconManagerUtils not found");
                return;
            }

            clearBlockLists(utilsClass);

            try {
                hookClassInitializer(utilsClass).intercept(chain -> {
                    Object res = chain.proceed();
                    clearBlockLists(utilsClass);
                    return res;
                });
            } catch (Throwable ignored) {}

            Logger.log("Hooked MiuiIconManagerUtils successfully");
        } catch (Throwable t) {
            Logger.log("Failed to hook MiuiIconManagerUtils", t);
        }
    }

    private void clearBlockLists(Class<?> utilsClass) {
        try {
            Field rightBlockField = findField(utilsClass, "RIGHT_BLOCK_LIST");
            if (rightBlockField != null) {
                rightBlockField.setAccessible(true);
                Object list = rightBlockField.get(null);
                if (list instanceof List) {
                    int originalSize = ((List<?>) list).size();
                    ((List<?>) list).clear();
                    Logger.log("Cleared RIGHT_BLOCK_LIST (was " + originalSize + " items)");
                }
            }

            Field miniRightBlockField = findField(utilsClass, "MINI_RIGHT_BLOCK_LIST");
            if (miniRightBlockField != null) {
                miniRightBlockField.setAccessible(true);
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

    private void hookMiuiStatusIconContainer(ClassLoader cl) {
        try {
            Class<?> containerClass = findClass(cl, "com.android.systemui.statusbar.views.MiuiStatusIconContainer");
            if (containerClass == null) {
                Logger.log("MiuiStatusIconContainer not found");
                return;
            }

            // Hook constructors
            for (Constructor<?> ctor : containerClass.getDeclaredConstructors()) {
                try {
                    hook(ctor).intercept(chain -> {
                        Object res = chain.proceed();
                        Object thisObj = chain.getThisObject();
                        setFieldValue(thisObj, "mShouldRestrictIcons", false);
                        setFieldValue(thisObj, "mNeedLimitIcon", false);
                        Logger.log("MiuiStatusIconContainer.<init>: restrictions set to false");
                        return res;
                    });
                } catch (Throwable ignored) {}
            }

            // Hook setIgnoredSlots(List)
            for (Method m : containerClass.getDeclaredMethods()) {
                if ("setIgnoredSlots".equals(m.getName())) {
                    try {
                        hook(m).intercept(chain -> {
                            Logger.log("MiuiStatusIconContainer.setIgnoredSlots intercepted -> overriding with empty list");
                            return chain.proceed(new Object[]{ new ArrayList<String>() });
                        });
                    } catch (Throwable ignored) {}
                }
            }

            // Hook setShouldRestrictIcons(boolean)
            for (Method m : containerClass.getDeclaredMethods()) {
                if ("setShouldRestrictIcons".equals(m.getName())) {
                    try {
                        hook(m).intercept(chain -> {
                            return chain.proceed(new Object[]{ false });
                        });
                    } catch (Throwable ignored) {}
                }
            }

            // Hook setNeedLimitIcon(boolean)
            for (Method m : containerClass.getDeclaredMethods()) {
                if ("setNeedLimitIcon".equals(m.getName())) {
                    try {
                        hook(m).intercept(chain -> {
                            return chain.proceed(new Object[]{ false });
                        });
                    } catch (Throwable ignored) {}
                }
            }

            // Hook onMeasure
            for (Method m : containerClass.getDeclaredMethods()) {
                if ("onMeasure".equals(m.getName())) {
                    try {
                        hook(m).intercept(chain -> {
                            ViewGroup container = (ViewGroup) chain.getThisObject();
                            setFieldValue(container, "mShouldRestrictIcons", false);
                            setFieldValue(container, "mNeedLimitIcon", false);

                            int childCount = container.getChildCount();
                            int widthSpec = (int) chain.getArg(0);
                            int heightSpec = (int) chain.getArg(1);

                            if (childCount > 6) {
                                setFieldValue(container, "mIconSpacing", 3);
                            }

                            int specMode = View.MeasureSpec.getMode(widthSpec);
                            int specSize = View.MeasureSpec.getSize(widthSpec);
                            if (specMode == View.MeasureSpec.AT_MOST && specSize < 700) {
                                widthSpec = View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.AT_MOST);
                            }

                            Object res = chain.proceed(new Object[]{ widthSpec, heightSpec });

                            Logger.log("MiuiStatusIconContainer.onMeasure: measured=(" +
                                    container.getMeasuredWidth() + "x" + container.getMeasuredHeight() +
                                    "), childCount=" + childCount);
                            return res;
                        });
                    } catch (Throwable ignored) {}
                }
            }

            // Hook onLayout
            for (Method m : containerClass.getDeclaredMethods()) {
                if ("onLayout".equals(m.getName())) {
                    try {
                        hook(m).intercept(chain -> {
                            ViewGroup container = (ViewGroup) chain.getThisObject();
                            setFieldValue(container, "mShouldRestrictIcons", false);
                            setFieldValue(container, "mNeedLimitIcon", false);

                            Object res = chain.proceed();

                            try {
                                layoutStatusIcons(container);
                            } catch (Throwable t) {
                                Logger.log("Error in layoutStatusIcons", t);
                            }

                            return res;
                        });
                    } catch (Throwable ignored) {}
                }
            }

            Logger.log("Hooked MiuiStatusIconContainer successfully");
        } catch (Throwable t) {
            Logger.log("Failed to hook MiuiStatusIconContainer", t);
        }
    }

    private void layoutStatusIcons(ViewGroup container) {
        int childCount = container.getChildCount();
        int containerWidth = container.getWidth();
        int paddingEnd = container.getPaddingEnd();

        Logger.log("MiuiStatusIconContainer.onLayout: containerWidth=" + containerWidth + ", childCount=" + childCount);

        List<View> visibleChildren = new ArrayList<>();
        for (int i = 0; i < childCount; i++) {
            View child = container.getChildAt(i);
            if (child == null) continue;

            Object tag = child.getTag();
            if (tag == null) continue;

            boolean isBlocked = false;
            try {
                Method m = child.getClass().getMethod("isIconBlocked");
                Object b = m.invoke(child);
                if (b instanceof Boolean) isBlocked = (Boolean) b;
            } catch (Throwable ignored) {}

            boolean isVisible = true;
            try {
                Method m = child.getClass().getMethod("isIconVisible");
                Object v = m.invoke(child);
                if (v instanceof Boolean) isVisible = (Boolean) v;
            } catch (Throwable ignored) {}

            String slot = "unknown";
            try {
                Method m = child.getClass().getMethod("getSlot");
                Object s = m.invoke(child);
                if (s != null) slot = s.toString();
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
                if (childW <= 0) childW = child.getMeasuredWidth();
                if (childW <= 0) childW = 32;

                currentX -= childW;
                int posX = Math.max(0, currentX);

                child.setVisibility(View.VISIBLE);
                child.setAlpha(1.0f);
                child.setTranslationX(posX);

                if (tag != null) {
                    setFieldValue(tag, "visibleState", 0); // VISIBLE
                    setFieldValue(tag, "xTranslation", (float) posX);
                    setFieldValue(tag, "alpha", 1.0f);
                }

                currentX -= spacing;
            } else {
                child.setVisibility(View.GONE);
                if (tag != null) {
                    setFieldValue(tag, "visibleState", 2); // GONE
                }
            }
        }
    }

    private void hookAospStatusIconContainer(ClassLoader cl) {
        try {
            Class<?> aospClass = findClass(cl, "com.android.systemui.statusbar.phone.StatusIconContainer");
            if (aospClass == null) return;

            for (Constructor<?> ctor : aospClass.getDeclaredConstructors()) {
                try {
                    hook(ctor).intercept(chain -> {
                        Object res = chain.proceed();
                        setFieldValue(chain.getThisObject(), "mShouldRestrictIcons", false);
                        return res;
                    });
                } catch (Throwable ignored) {}
            }

            for (Method m : aospClass.getDeclaredMethods()) {
                if ("setShouldRestrictIcons".equals(m.getName())) {
                    try {
                        hook(m).intercept(chain -> chain.proceed(new Object[]{ false }));
                    } catch (Throwable ignored) {}
                }
                if ("onMeasure".equals(m.getName()) || "onLayout".equals(m.getName())) {
                    try {
                        hook(m).intercept(chain -> {
                            setFieldValue(chain.getThisObject(), "mShouldRestrictIcons", false);
                            return chain.proceed();
                        });
                    } catch (Throwable ignored) {}
                }
            }

            Logger.log("Hooked AOSP StatusIconContainer successfully");
        } catch (Throwable t) {
            Logger.log("Failed to hook AOSP StatusIconContainer", t);
        }
    }

    private void hookMiuiPhoneStatusBarView(ClassLoader cl) {
        try {
            Class<?> viewClass = findClass(cl, "com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView");
            if (viewClass == null) return;

            for (Method m : viewClass.getDeclaredMethods()) {
                if ("updateCutoutLocation".equals(m.getName())) {
                    try {
                        hook(m).intercept(chain -> {
                            Object res = chain.proceed();
                            Object thisObj = chain.getThisObject();

                            try {
                                Object statusIcons = getFieldValue(thisObj, "mStatusBarStatusIcons");
                                if (statusIcons != null) {
                                    Method setIgnored = statusIcons.getClass().getMethod("setIgnoredSlots", List.class);
                                    setIgnored.invoke(statusIcons, new ArrayList<String>());
                                    setFieldValue(statusIcons, "mShouldRestrictIcons", false);
                                    setFieldValue(statusIcons, "mNeedLimitIcon", false);
                                    Logger.log("MiuiPhoneStatusBarView.updateCutoutLocation: cleared status icon restrictions");
                                }

                                Object leftContainer = getFieldValue(thisObj, "mStatusBarLeftContainer");
                                Object systemIconArea = getFieldValue(thisObj, "mSystemIconArea");

                                if (leftContainer instanceof View && systemIconArea instanceof View) {
                                    View leftView = (View) leftContainer;
                                    View rightView = (View) systemIconArea;

                                    ViewGroup.LayoutParams leftLp = leftView.getLayoutParams();
                                    ViewGroup.LayoutParams rightLp = rightView.getLayoutParams();

                                    if (leftLp instanceof LinearLayout.LayoutParams && rightLp instanceof LinearLayout.LayoutParams) {
                                        LinearLayout.LayoutParams rightLinLp = (LinearLayout.LayoutParams) rightLp;
                                        LinearLayout.LayoutParams leftLinLp = (LinearLayout.LayoutParams) leftLp;

                                        leftLinLp.weight = 0.8f;
                                        rightLinLp.weight = 1.2f;

                                        leftView.setLayoutParams(leftLinLp);
                                        rightView.setLayoutParams(rightLinLp);
                                        Logger.log("Adjusted container weights: left=0.8, right=1.2");
                                    }
                                }
                            } catch (Throwable t) {
                                Logger.log("Error in updateCutoutLocation after", t);
                            }

                            return res;
                        });
                    } catch (Throwable ignored) {}
                }
            }

            Logger.log("Hooked MiuiPhoneStatusBarView successfully");
        } catch (Throwable t) {
            Logger.log("Failed to hook MiuiPhoneStatusBarView", t);
        }
    }

    // --- Helper Reflection Methods ---

    private Class<?> findClass(ClassLoader cl, String name) {
        try {
            return cl.loadClass(name);
        } catch (Throwable t) {
            return null;
        }
    }

    private Field findField(Class<?> clazz, String fieldName) {
        Class<?> curr = clazz;
        while (curr != null && curr != Object.class) {
            try {
                Field f = curr.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f;
            } catch (Throwable ignored) {}
            curr = curr.getSuperclass();
        }
        return null;
    }

    private Object getFieldValue(Object obj, String fieldName) {
        if (obj == null) return null;
        try {
            Field f = findField(obj.getClass(), fieldName);
            if (f != null) {
                return f.get(obj);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void setFieldValue(Object obj, String fieldName, Object val) {
        if (obj == null) return;
        try {
            Field f = findField(obj.getClass(), fieldName);
            if (f != null) {
                f.set(obj, val);
            }
        } catch (Throwable ignored) {}
    }
}
