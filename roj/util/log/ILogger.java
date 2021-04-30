package roj.util.log;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: ILogger.java
 */
public interface ILogger {
    void info(Object text);

    void debug(Object text);

    void warn(Object text);

    void error(Object text);

    void catching(Throwable throwable);
}
