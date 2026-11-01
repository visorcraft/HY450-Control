package com.visorcraft.hy450control;

import android.content.Context;

final class Vendor {
    static Object display() throws Exception {
        return Class.forName("com.softwinner.tv.AwTvDisplayManager")
                .getMethod("getInstance").invoke(null);
    }

    static Object audio(Context context) throws Exception {
        return Class.forName("com.softwinner.tv.AwTvAudioManager")
                .getMethod("getInstance", Context.class).invoke(null, context);
    }

    static Object source(Context context) throws Exception {
        return Class.forName("com.softwinner.tv.AwTvSourceManager")
                .getConstructor(Context.class).newInstance(context);
    }

    static Object enumValue(String className, String name) throws Exception {
        return Class.forName(className).getField(name).get(null);
    }

    static Object[] enumValues(String className) throws Exception {
        return (Object[]) Class.forName(className).getMethod("values").invoke(null);
    }

    static Object call(Object target, String method, Class<?>[] types, Object... args)
            throws Exception {
        return target.getClass().getMethod(method, types).invoke(target, args);
    }
}
