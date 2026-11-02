package com.visorcraft.hy450control;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

final class ControlCatalog {
    static VendorIntControl backlight() throws Exception {
        return new VendorIntControl(Vendor.display(), "getBacklight", "setBacklight", null, 0, 255);
    }

    static List<Control> picture(Context context) throws Exception {
        List<Control> result = new ArrayList<>();
        Object manager = Vendor.display();
        result.add(new Control("display.backlight", "Backlight", backlight()));
        addBasic(result, manager, "Brightness", "E_AW_PQ_BASIC_TYPE_BRIGHTNESS");
        addBasic(result, manager, "Contrast", "E_AW_PQ_BASIC_TYPE_CONTRAST");
        addBasic(result, manager, "Hue", "E_AW_PQ_BASIC_TYPE_HUE");
        addBasic(result, manager, "Saturation", "E_AW_PQ_BASIC_TYPE_SATURATION");
        addBasic(result, manager, "Sharpness", "E_AW_PQ_BASIC_TYPE_SHARPNESS");
        result.add(new Control("display.pictureMode", "Picture mode", new VendorEnumControl(manager,
                "getPictureModeName", "setPictureModeByName",
                Class.forName("com.softwinner.tv.common.AwTvDisplayTypes$EnumPictureMode"),
                Vendor.enumValues("com.softwinner.tv.common.AwTvDisplayTypes$EnumPictureMode"))));
        result.add(new Control("display.colorTemp", "Colour temperature", new VendorEnumControl(manager,
                "getColorTemp", "setColorTemp",
                Class.forName("com.softwinner.tv.common.AwTvDisplayTypes$EnumColorTempMode"),
                Vendor.enumValues("com.softwinner.tv.common.AwTvDisplayTypes$EnumColorTempMode"))));
        result.add(new Control("display.gamma", "Gamma",
                new VendorIntControl(manager, "getGammaFactor", "setGammaFactor", null, 0, 5)));
        result.add(new Control("display.snr", "SNR noise reduction",
                new VendorIntControl(manager, "getSNR", "setSNR", null, 0, 100)));
        result.add(new Control("display.tnr", "TNR noise reduction",
                new VendorIntControl(manager, "getTNR", "setTNR", null, 0, 100)));
        result.add(new Control("display.dynamicBacklight", "Dynamic backlight",
                new VendorBooleanControl(manager, "getDynamicBacklight", "setDynamicBacklight")));
        result.add(new Control("display.aspect", "Aspect ratio", new VendorEnumControl(manager,
                "getSourceAspectRatio", "setSourceAspectRatio",
                Class.forName("com.softwinner.tv.common.AwTvDisplayTypes$EnumOverScanScreenMode"),
                withoutMax("com.softwinner.tv.common.AwTvDisplayTypes$EnumOverScanScreenMode"))));
        result.add(new Control("display.hdmiMode", "HDMI video/PC mode", new VendorEnumControl(manager,
                "getHDMIVideoPCMode", "setHDMIVideoPCMode",
                Class.forName("com.softwinner.tv.common.AwTvDisplayTypes$EnumVideoPCMode"),
                Vendor.enumValues("com.softwinner.tv.common.AwTvDisplayTypes$EnumVideoPCMode"))));
        result.add(new Control("display.videoRange", "Video range",
                new VendorIntControl(manager, "getVideoRange", "setVideoRange", null, 0, 2)));
        return result;
    }

    static List<Control> audio(Context context) throws Exception {
        List<Control> result = new ArrayList<>();
        Object manager = Vendor.audio(context);
        result.add(new Control("audio.mode", "Audio mode", new VendorEnumControl(manager,
                "getAudioMode", "setAudioMode",
                Class.forName("com.softwinner.tv.common.AwTvAudioTypes$EnumGEQType"),
                Vendor.enumValues("com.softwinner.tv.common.AwTvAudioTypes$EnumGEQType"))));
        for (int i = 1; i <= 7; i++) {
            Object band = Vendor.enumValue("com.softwinner.tv.common.AwTvAudioTypes$EnumGEQBand",
                    "E_AW_GEQ_BAND_" + i);
            result.add(new Control("audio.eq" + i, "EQ band " + i,
                    new VendorIntControl(manager, "getAudioEqBand", "setAudioEqBand", band, -12, 12)));
        }
        result.add(new Control("audio.balance", "Balance",
                new VendorIntControl(manager, "getAudioOutputBalance", "setAudioOutputBalance", 0, -100, 100)));
        Object speaker = Vendor.enumValue("com.softwinner.tv.common.AwTvAudioTypes$EnumAudioOutMode",
                "E_AW_AUDIO_OUT_MODE_SPEAKER");
        result.add(new Control("audio.delay", "Audio delay (ms)",
                new VendorIntControl(manager, "getAudioOutputDelay", "setAudioOutputDelay", speaker, 0, 500)));
        result.add(new Control("audio.out", "Audio output", new VendorEnumControl(manager,
                "getSoundOutMode", "setSoundOutMode",
                Class.forName("com.softwinner.tv.common.AwTvAudioTypes$EnumAudioOutMode"),
                Vendor.enumValues("com.softwinner.tv.common.AwTvAudioTypes$EnumAudioOutMode"))));
        result.add(new Control("audio.surround", "Surround", new VendorEnumControl(manager,
                "getAudioSurroundMode", "setAudioSurroundMode",
                Class.forName("com.softwinner.tv.common.AwTvAudioTypes$EnumSurroundMode"),
                Vendor.enumValues("com.softwinner.tv.common.AwTvAudioTypes$EnumSurroundMode"))));
        result.add(new Control("audio.avl", "Automatic volume limiting",
                new VendorBooleanControl(manager, "getAvlEnable", "setAvlEnable")));
        result.add(new Control("audio.avlLimit", "AVL limit",
                new VendorIntControl(manager, "getAvlLimit", "setAvlLimit", null, 0, 100)));
        return result;
    }

    static List<Control> source(Context context) throws Exception {
        List<Control> result = new ArrayList<>();
        Object manager = Vendor.source(context);
        result.add(new Control("source.current", "Current input",
                new VendorIntControl(manager, "getCurrentInputSource", "setCurrentInputSource", null, 2, 6)));
        result.add(new Control("source.freeze", "Freeze input",
                new VendorBooleanControl(manager, "getSourceFreezeState", "setSourceFreezeState")));
        result.add(new Control("source.blueScreen", "Blue screen when idle",
                new VendorBooleanControl(manager, "getBlueScreenEnable", "setBlueScreenEnable")));
        result.add(new Control("source.bootDefault", "Boot default (read only)",
                new ReadOnlyControl(manager, "getBootDefaultInputSource")));
        return result;
    }

    private static void addBasic(List<Control> result, Object manager, String title, String name)
            throws Exception {
        Object basic = Vendor.enumValue("com.softwinner.tv.common.AwTvDisplayTypes$EnumPQBasicType", name);
        result.add(new Control("display." + name, title,
                new VendorIntControl(manager, "getBasicControl", "setBasicControl", basic, 0, 100)));
    }

    private static Object[] withoutMax(String className) throws Exception {
        Object[] values = Vendor.enumValues(className);
        Object[] result = new Object[values.length - 1];
        System.arraycopy(values, 0, result, 0, result.length);
        return result;
    }
}
