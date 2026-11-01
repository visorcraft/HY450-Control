package com.visorcraft.hy450control;

final class Errors {
    static String describe(Exception error) {
        String message = error.getMessage();
        return message != null ? message : error.getClass().getSimpleName();
    }
}
