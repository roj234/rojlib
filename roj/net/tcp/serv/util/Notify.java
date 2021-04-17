package roj.net.tcp.serv.util;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2021/2/4 15:40
 */
public final class Notify extends RuntimeException {
    public final int code;

    public Notify(int code) {
        super("Notify");
        this.code = code;
    }

    public Notify(Throwable e) {
        super(e);
        this.code = 1;
    }
}
