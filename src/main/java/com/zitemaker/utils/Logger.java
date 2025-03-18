package com.zitemaker.utils;

public class Logger {
    private final PlatformLogger logger;
    private final boolean color;

    public Logger(PlatformLogger logger, boolean color) {
        this.logger = logger;
        this.color = color;
    }

    public void info(String message) {
        log(message);
    }

    private void log(String message) {
        logger.log(LogLevel.INFO, formatMessage(message));
    }

    private String formatMessage(String message) {
        message = color ? "\u00a7e[\u00a72ArenaRegen\u00a7e] \u00a7r%s%s".formatted(
                "", message) : message;
        message += "\u00a7r";
        message = ANSIConverter.convertToAnsi(message);
        return message;
    }
}
