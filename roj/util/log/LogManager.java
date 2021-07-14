/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package roj.util.log;

import org.apache.logging.log4j.Logger;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
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
