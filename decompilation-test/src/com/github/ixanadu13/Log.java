package com.github.ixanadu13;

import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

public class Log {
    private static final Logger logger = (Logger) LoggerFactory.getLogger("CFR_TEST");
    public static void info(String msg){
        logger.info(msg);
    }
    public static void error(String msg){
        logger.error(msg);
    }
    public static void error(String fmt, Object... args){
        error(String.format(fmt, args));
    }
    public static void debug(String msg) {
        logger.debug(msg);
    }
    public static void debug(String fmt, Object... args){
        debug(String.format(fmt, args));
    }
}
