package com.zitemaker.utils;

import java.util.logging.Level;
import java.util.logging.Logger;

public class JavaPlatformLogger implements PlatformLogger {
    private final Console console;
    private final Logger logger;

    public JavaPlatformLogger(Console console, Logger logger) {
        this.console = console;
        this.logger = logger;
    }

    @Override
    public void log(LogLevel level, String message) {
        switch (level) {
            case INFO -> console.sendMessage(message);
            case WARNING -> logger.warning(message);
            case SEVERE -> logger.severe(message);
        }
    }

    @Override
    public void log(LogLevel level, String message, Throwable throwable) {
        switch (level) {
            case INFO -> logger.log(Level.INFO, message, throwable);
            case WARNING -> logger.log(Level.WARNING, message, throwable);
            case SEVERE -> logger.log(Level.SEVERE, message, throwable);
        }
    }


    public Console getConsole() {
        return console;
    }


    public Logger getLogger() {
        return logger;
    }
}
