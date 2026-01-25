package com.pxbtdev.utils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Logger {

    public static void info(String message) {
        log.info(message);
    }

    public static void error(String message, Throwable throwable) {
        log.error(message, throwable);
    }

    public static void debug(String message) {
        log.debug(message);
    }

    public static void warn(String message) {
        log.warn(message);
    }
}