package roj.kscript.api;

import roj.config.ParseException;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2021/1/10 1:28
 */
public interface ErrorHandler {
    void handle(String type, String file, ParseException e);
}
