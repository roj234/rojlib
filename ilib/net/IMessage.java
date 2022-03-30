package ilib.net;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.PacketBuffer;

/**
 * @author Roj234
 * @since 2022/4/15 11:35
 */
public interface IMessage extends net.minecraftforge.fml.common.network.simpleimpl.IMessage {
    @Override
    @Deprecated
    default void fromBytes(ByteBuf buf) {
        fromBytes(new PacketBuffer(buf));
    }

    @Override
    @Deprecated
    default void toBytes(ByteBuf buf) {
        toBytes(new PacketBuffer(buf));
    }

    void fromBytes(PacketBuffer buf);

    void toBytes(PacketBuffer buf);
}