package roj.util.log;

import org.apache.logging.log4j.Logger;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: LogManager.java
 */
public class LogManager {
    public static ILogger getLogger(String name) {
        try {
            Logger log = org.apache.logging.log4j.LogManager.getLogger(name);
            return new ApacheLogger(log);
        } catch (Throwable e) {
            return new StdOutLogger();
        }
    }

    private static class ApacheLogger implements ILogger {
        private final Logger log;

        public ApacheLogger(Logger log) {
            this.log = log;
        }

        @Override
        public void info(Object text) {
            log.info(text);
        }

        @Override
        public void debug(Object text) {
            log.debug(text);
        }

        @Override
        public void warn(Object text) {
            log.warn(text);
        }

        @Override
        public void error(Object text) {
            log.error(text);
        }

        @Override
        public void catching(Throwable throwable) {
            log.catching(throwable);
        }
    }

    private static class StdOutLogger implements ILogger {
        @Override
        public void info(Object text) {
            System.out.println(text);
        }

        @Override
        public void debug(Object text) {}

        @Override
        public void warn(Object text) {
            System.err.println(text);
        }

        @Override
        public void error(Object text) {
            System.err.println(text);
        }

        @Override
        public void catching(Throwable throwable) {
            throwable.printStackTrace(System.err);
        }
    }
}
