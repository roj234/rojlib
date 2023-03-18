package ilib.api.energy;

import net.minecraft.nbt.NBTTagCompound;

import net.minecraftforge.common.util.INBTSerializable;

// ME unit define
public interface MEItem extends INBTSerializable<NBTTagCompound> {
	enum EnergyType {
		RECEIVER, STORAGE, PROVIDER, TUBE
	}

	EnergyType getEnergyType();

	int currentME();

	int maxME();

	int voltage();

	boolean canExtract();

	boolean canReceive();

	int receiveSpeed();

	int extractSpeed();

	int receiveME(int count, boolean simulate);

	int extractME(int count, boolean simulate);
}