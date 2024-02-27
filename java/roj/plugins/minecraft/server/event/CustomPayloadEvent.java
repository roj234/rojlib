package roj.plugins.minecraft.server.event;

import roj.asmx.event.Event;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2024/3/22 0022 1:19
 */
public class CustomPayloadEvent extends Event {
	private final String channel;
	private final DynByteBuf data;

	public CustomPayloadEvent(DynByteBuf data) {
		this.data = data;
		this.channel = data.readVarIntUTF(255);
	}

	public String getChannel() { return channel; }
	public DynByteBuf getData() { return data.slice(); }
}