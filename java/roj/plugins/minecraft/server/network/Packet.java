package roj.plugins.minecraft.server.network;

import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2024/3/19 0019 15:52
 */
public class Packet {
	public final String name;
	final DynByteBuf data;

	public Packet(String name, DynByteBuf buf) {
		this.name = name;
		this.data = buf;
	}

	public DynByteBuf getData() { return data; }
}