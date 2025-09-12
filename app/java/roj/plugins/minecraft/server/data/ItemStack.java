package roj.plugins.minecraft.server.data;

import roj.config.NbtEncoder;
import roj.config.NbtParser;
import roj.config.node.MapValue;
import roj.text.ParseException;
import roj.util.DynByteBuf;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/3/20 8:01
 */
public class ItemStack {
	public static ItemStack EMPTY = new ItemStack(Item.AIR, 0);

	public Item item;
	public byte count;
	public MapValue nbt;

	public ItemStack(Item item) {
		this.item = item;
		count = 1;
	}
	public ItemStack(Item item, int count) {
		this.item = item;
		this.count = (byte) count;
	}
	public ItemStack(DynByteBuf net) throws IOException {
		if (net.readBoolean()) {
			this.item = Item.getItem(net.readVarInt());
			this.count = net.readByte();
			if (net.get(net.rIndex) != 0) {
				try {
					this.nbt = new NbtParser().parse(net).asMap();
				} catch (ParseException e) {
					throw new IOException("NBT解析失败", e);
				}
			} else {
				net.rIndex++;
			}
		} else {
			this.item = EMPTY.item;
			this.count = 0;
		}
	}

	public MapValue tag() {
		if (nbt == null) nbt = new MapValue();
		return nbt;
	}
	public MapValue getSubTag(String name) {
		if (nbt == null) nbt = new MapValue();
		return nbt.getOrCreateMap(name);
	}

	public boolean isEmpty() {
		return count == 0 || this == EMPTY;
	}

	public DynByteBuf toMinecraftPacket(DynByteBuf buf) {
		if (isEmpty()) return buf.putBool(false);

		buf.putBool(true).putVarInt(item.id).put(count);
		if (nbt != null) {
			NbtEncoder ser = new NbtEncoder(buf);
			nbt.accept(ser);
		} else {
			buf.put(0);
		}
		return buf;
	}
}