package com.hyperos.statusbar.expand;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

public class StatusBarExpandModule extends XposedModule {

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
        Logger.log("Unlimited status icons mode active");
        Logger.log("==================================================");

        ClassLoader cl = param.getClassLoader();
        hookMiuiIconManagerUtils(cl);
        hookMiuiStatusIconContainer(cl);
        hookMiuiDripLeftStatusIconContainer(cl);
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
        String[] fieldNames = new String[]{
                "RIGHT_BLOCK_LIST",
                "MINI_RIGHT_BLOCK_LIST",
                "CONTROL_CENTER_BLOCK_LIST"
        };
        for (String fn : fieldNames) {
            try {
                Field f = findField(utilsClass, fn);
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
                        hook(m).intercept(chain -> chain.proceed(new Object[]{ false }));
                    } catch (Throwable ignored) {}
                }
            }

            // Hook setNeedLimitIcon(boolean)
            for (Method m : containerClass.getDeclaredMethods()) {
                if ("setNeedLimitIcon".equals(m.getName())) {
                    try {
                        hook(m).intercept(chain -> chain.proceed(new Object[]{ false }));
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
                            setFieldValue(container, "mIconSpacing", 3);

                            int widthSpec = (int) chain.getArg(0);
                            int heightSpec = (int) chain.getArg(1);
                            int specMode = View.MeasureSpec.getMode(widthSpec);
                            int specSize = View.MeasureSpec.getSize(widthSpec);

                            if (specMode != View.MeasureSpec.EXACTLY || specSize < 1000) {
                                widthSpec = View.MeasureSpec.makeMeasureSpec(4096, View.MeasureSpec.AT_MOST);
                            }

                            Object res = chain.proceed(new Object[]{ widthSpec, heightSpec });

                            Logger.log("MiuiStatusIconContainer.onMeasure: measured=(" +
                                    container.getMeasuredWidth() + "x" + container.getMeasuredHeight() +
                                    "), childCount=" + container.getChildCount());
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

    private void hookMiuiDripLeftStatusIconContainer(ClassLoader cl) {
        try {
            Class<?> dripClass = findClass(cl, "com.android.systemui.statusbar.views.MiuiDripLeftStatusIconContainer");
            if (dripClass == null) return;

            for (Method m : dripClass.getDeclaredMethods()) {
                if ("setIgnoredSlots".equals(m.getName())) {
                    try {
                        hook(m).intercept(chain -> chain.proceed(new Object[]{ new ArrayList<String>() }));
                    } catch (Throwable ignored) {}
                }
                if ("setShouldRestrictIcons".equals(m.getName())) {
                    try {
                        hook(m).intercept(chain -> chain.proceed(new Object[]{ false }));
                    } catch (Throwable ignored) {}
                }
                if ("setNeedLimitIcon".equals(m.getName())) {
                    try {
                        hook(m).intercept(chain -> chain.proceed(new Object[]{ false }));
                    } catch (Throwable ignored) {}
                }
            }
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

        // 2. Disable clipping on container and parent hierarchy so icons can expand to the left without being clipped
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

        // 3. Filter eligible displayable icons
        List<View> eligibleChildren = new ArrayList<>();
        for (int i = 0; i < childCount; i++) {
            View child = container.getChildAt(i);
            if (child == null) continue;

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

            if (!isBlocked && isVisible) {
                eligibleChildren.add(child);
            } else {
                child.setVisibility(View.GONE);
                try {
                    Method m = child.getClass().getMethod("setVisibleState", int.class, boolean.class);
                    m.invoke(child, 2, false);
                } catch (Throwable ignored) {}
                Object tag = getIconState(child, tagId);
                if (tag != null) {
                    setFieldValue(tag, "visibleState", 2);
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
                // Dynamically tighten spacing so all icons fit seamlessly
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
                Method m = child.getClass().getMethod("setVisibleState", int.class, boolean.class);
                m.invoke(child, 0, false);
            } catch (Throwable ignored) {}

            Object tag = getIconState(child, tagId);
            if (tag != null) {
                setFieldValue(tag, "visibleState", 0);
                setFieldValue(tag, "xTranslation", posX);
                setFieldValue(tag, "alpha", 1.0f);
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
                                if (thisObj instanceof ViewGroup) {
                                    ((ViewGroup) thisObj).setClipChildren(false);
                                    ((ViewGroup) thisObj).setClipToPadding(false);
                                }

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

                                        leftLinLp.weight = 0.7f;
                                        rightLinLp.weight = 1.3f;

                                        leftView.setLayoutParams(leftLinLp);
                                        rightView.setLayoutParams(rightLinLp);
                                        Logger.log("Adjusted container weights: left=0.7, right=1.3");
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
