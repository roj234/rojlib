package ilib.api.energy;

import ilib.capabilities.INBTSerializable;

// ME unit define
public interface MEItem extends INBTSerializable {
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