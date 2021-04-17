package roj.net.udp;

import javax.annotation.Nullable;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/9/20 15:39
 */
public interface MessageHandler {
    /**
     * @return 发回的消息
     */
    @Nullable
    byte[] onMessage(byte[] list, int length, int fromPort);
}
