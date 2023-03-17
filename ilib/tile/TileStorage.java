package ilib.tile;

import ilib.api.TileRegister;
import roj.collect.Int2IntMap;

import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
@TileRegister("ilib:storage")
public class TileStorage extends TileBase {
	private final Int2IntMap data = new Int2IntMap();

	public TileStorage() {
		super();
	}

	public int getOr(int a, int b) {
		return data.getOrDefaultInt(a, b);
	}

	@Deprecated
	public Integer get(int id) {
		return data.get(id);
	}

	public void set(int id, int value) {
		data.put(id, value);
		sendDataUpdate();
	}

	@Nonnull
	@Override
	public NBTTagCompound writeToNBT(@Nonnull NBTTagCompound tag) {
		int[] array = new int[data.size() << 1];
		int i = 0;
		for (Int2IntMap.Entry entry : data.selfEntrySet()) {
			array[i++] = entry.getIntKey();
			array[i++] = entry.v;
		}
		tag.setIntArray("X", array);

		return super.writeToNBT(tag);
	}

	@Override
	public void readFromNBT(@Nonnull NBTTagCompound tag) {
		super.readFromNBT(tag);

		int[] list = tag.getIntArray("X");
		data.clear();
		if (list.length > 0 && (list.length & 1) == 0) {
			for (int i = 0; i < list.length; ) {
				data.put(list[i++], list[i++]);
			}
		}
	}

}