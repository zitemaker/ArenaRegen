package com.zitemaker.utils;

import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;
import java.util.logging.Logger;

public record JavaPlatformLogger(Console console, Logger logger) implements PlatformLogger {

    @Override
    public void log(@NotNull LogLevel level, String message) {
        switch (level) {
            case INFO -> console.sendMessage(message);
            case WARNING -> logger.warning(message);
            case SEVERE -> logger.severe(message);
        }
    }

    @Override
    public void log(@NotNull LogLevel level, String message, Throwable throwable) {
        switch (level) {
            case INFO -> logger.log(Level.INFO, message, throwable);
            case WARNING -> logger.log(Level.WARNING, message, throwable);
            case SEVERE -> logger.log(Level.SEVERE, message, throwable);
        }
    }
}
