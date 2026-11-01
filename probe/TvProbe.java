import java.lang.reflect.Method;

public final class TvProbe {
    public static void main(String[] args) throws Exception {
        Class<?> type = Class.forName("com.softwinner.tv.AwTvDisplayManager");
        Object display = type.getMethod("getInstance").invoke(null);
        if (args.length == 2 && args[0].equals("set-backlight")) {
            int value = Integer.parseInt(args[1]);
            Method setter = type.getMethod("setBacklight", int.class);
            System.out.println("setBacklight(" + value + ")=" + setter.invoke(display, value));
        }
        System.out.println("backlight=" + type.getMethod("getBacklight").invoke(display));
        System.out.println("pictureMode=" + type.getMethod("getPictureModeName").invoke(display));
        System.out.println("colorTemp=" + type.getMethod("getColorTemp").invoke(display));
        System.out.println("gamma=" + type.getMethod("getGammaFactor").invoke(display));
        System.out.println("dynamicBacklight=" + type.getMethod("getDynamicBacklight").invoke(display));
    }
}
