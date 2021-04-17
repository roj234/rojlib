package roj.config.word;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/10/17 1:18
 */
@FunctionalInterface
public interface LineHandler {
    void handleLineNumber(int line);
}
