package roj.net.handler;

import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2025/10/16 9:51
 */
public interface Packet {
    void encode(DynByteBuf buf);
}
