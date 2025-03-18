package com.zitemaker.utils;

import org.jetbrains.annotations.NotNull;

public interface PlatformLogger {
    void log(LogLevel level, String message);

    void log(@NotNull LogLevel level, String message, Throwable throwable);
}