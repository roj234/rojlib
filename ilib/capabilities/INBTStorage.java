package ilib.capabilities;

import roj.util.Helpers;

import net.minecraft.nbt.NBTBase;
import net.minecraft.util.EnumFacing;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.INBTSerializable;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class INBTStorage<T extends INBTSerializable<?>> implements Capability.IStorage<T> {
	public NBTBase writeNBT(Capability<T> o, T data, EnumFacing side) { return data.serializeNBT(); }
	public void readNBT(Capability<T> o, T data, EnumFacing side, NBTBase nbt) { data.deserializeNBT(Helpers.cast(nbt)); }
}