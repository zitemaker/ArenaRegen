package com.zitemaker.utils;

public class Logger {
    private final PlatformLogger logger;
    private final boolean color;
    private boolean debug = false;

    public Logger(PlatformLogger logger, boolean color) {
        this.logger = logger;
        this.color = color;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public void debug(String message) {
        debug(LogLevel.INFO, message);
    }

    public void debug(String message, Throwable thrown) {
        debug(LogLevel.WARNING, message, thrown);
    }

    public void debug(Throwable thrown) {
        debug(LogLevel.WARNING, "Received error", thrown);
    }

    public void debug(LogLevel level, String message) {
        if (!debug) {
            return;
        }
        log(level, message);
    }

    public void debug(LogLevel level, String message, Throwable thrown) {
        if (!debug) {
            return;
        }
        log(level, message, thrown);
    }

    public void info(String message) {
        log(LogLevel.INFO, message);
    }

    public void info(String message, Throwable thrown) {
        log(LogLevel.INFO, message, thrown);
    }

    public void warning(String message) {
        log(LogLevel.WARNING, message);
    }

    public void warning(String message, Throwable thrown) {
        log(LogLevel.WARNING, message, thrown);
    }

    public void severe(String message) {
        log(LogLevel.SEVERE, message);
    }

    public void severe(String message, Throwable thrown) {
        log(LogLevel.SEVERE, message, thrown);
    }

    private void log(LogLevel level, String message) {
        logger.log(level, formatMessage(level, message));
    }

    private void log(LogLevel level, String message, Throwable thrown) {
        logger.log(level, formatMessage(level, message), thrown);
    }

    private String formatMessage(LogLevel level, String message) {
        message = color ? "\u00a7e[\u00a72Jails\u00a7e] \u00a7r%s%s".formatted(
                switch (level) {
                    case INFO -> "";
                    case WARNING -> "\u00a7e";
                    case SEVERE -> "\u00a7c";
                }, message) : message;
        message += "\u00a7r";
        message = ANSIConverter.convertToAnsi(message);
        return message;
    }
}
