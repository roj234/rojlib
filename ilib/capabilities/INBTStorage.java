package ilib.capabilities;

import net.minecraft.nbt.NBTBase;
import net.minecraft.util.EnumFacing;

import net.minecraftforge.common.capabilities.Capability;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class INBTStorage<T extends INBTSerializable> implements Capability.IStorage<T> {
	public NBTBase writeNBT(Capability<T> o, T data, EnumFacing side) {
		return data.toNBT();
	}

	public void readNBT(Capability<T> o, T data, EnumFacing side, NBTBase nbt) {
		data.fromNBT(nbt);
	}
}