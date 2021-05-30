package roj.concurrent;

import java.io.IOException;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/2/17 21:38
 */
public interface WaitingIOFuture {
    void waitFor() throws IOException;
    boolean isDone();
    default String flag() {
        return "";
    }
}
