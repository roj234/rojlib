package roj.plugins.minecraft.server.data;

import roj.config.NBTParser;
import roj.config.ParseException;
import roj.config.data.CMap;
import roj.config.serial.ToNBT;
import roj.util.DynByteBuf;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/3/20 0020 8:01
 */
public class ItemStack {
	public static ItemStack EMPTY = new ItemStack(Item.AIR, 0);

	public Item item;
	public byte count;
	public CMap nbt;

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
					this.nbt = new NBTParser().parse(net).asMap();
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

	public CMap tag() {
		if (nbt == null) nbt = new CMap();
		return nbt;
	}
	public CMap getSubTag(String name) {
		if (nbt == null) nbt = new CMap();
		return nbt.getOrCreateMap(name);
	}

	public boolean isEmpty() {
		return count == 0 || this == EMPTY;
	}

	public DynByteBuf toMinecraftPacket(DynByteBuf buf) {
		if (isEmpty()) return buf.putBool(false);

		buf.putBool(true).putVarInt(item.id).put(count);
		if (nbt != null) {
			ToNBT ser = new ToNBT(buf);
			nbt.accept(ser);
		} else {
			buf.put(0);
		}
		return buf;
	}
}