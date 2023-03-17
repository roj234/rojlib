package ilib.net;

import net.minecraft.network.PacketBuffer;

/**
 * @author Roj234
 * @since 2022/4/15 11:35
 */
public interface IMessage {
	void fromBytes(PacketBuffer buf);

	void toBytes(PacketBuffer buf);
}