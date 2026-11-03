package com.visorcraft.hy450control;

final class Errors {
    static String describe(Exception error) {
        Throwable current = error;
        while (current instanceof java.lang.reflect.InvocationTargetException) {
            Throwable target = ((java.lang.reflect.InvocationTargetException) current)
                    .getTargetException();
            if (target == null) break;
            current = target;
        }
        String message = current.getMessage();
        return message != null && !message.trim().isEmpty()
                ? message : current.getClass().getSimpleName();
    }
}
