package roj.net.tcp.util;

import roj.collect.ToIntMap;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/11/28 20:17
 */
public final class Action {
    public static final int
            GET = 1,
            POST = 2,
            PUT = 3,
            HEAD = 4,
            DELETE = 5,
            OPTIONS = 6,
            TRACE = 7,
            CONNECT = 8;

    static final ToIntMap<String> available = new ToIntMap<>(7, 2);

    static {
        available.putInt("GET", 1);
        available.putInt("POST", 2);
        available.putInt("PUT", 3);
        available.putInt("HEAD", 4);
        available.putInt("DELETE", 5);
        available.putInt("OPTIONS", 6);
        available.putInt("TRACE", 7);
        available.putInt("CONNECT", 8);
    }

    public static int valueOf(String name) {
        return available.getOrDefault(name, -1);
    }
}
