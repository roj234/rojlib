package ilib.capabilities;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public interface INBTSerializable extends net.minecraftforge.common.util.INBTSerializable<NBTTagCompound> {
	default NBTBase toNBT() {
		return serializeNBT();
	}

	default void fromNBT(NBTBase base) {
		if (base instanceof NBTTagCompound) deserializeNBT((NBTTagCompound) base);
	}
}
