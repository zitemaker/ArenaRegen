package com.zitemaker.utils;

import org.bukkit.Bukkit;

public class SpigotConsole implements Console {
    @Override
    public void sendMessage(String message) {
        Bukkit.getConsoleSender().sendMessage(message);
    }
}
