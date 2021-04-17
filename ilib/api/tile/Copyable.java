package ilib.api.tile;

import net.minecraft.nbt.NBTTagCompound;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public interface Copyable {
	String getDataType();

	NBTTagCompound copy(NBTTagCompound tag);

	void paste(NBTTagCompound tag);
}
