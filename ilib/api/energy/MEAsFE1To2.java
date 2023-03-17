package ilib.api.energy;

import net.minecraftforge.energy.IEnergyStorage;

/**
 * @author Roj234
 */
public interface MEAsFE1To2 extends METile, IEnergyStorage {
	default int receiveEnergy(int maxReceive, boolean simulate) {
		return receiveME(maxReceive >> 1, simulate) << 1;
	}

	default int extractEnergy(int count, boolean simulate) {
		return extractME(count >> 1, simulate) << 1;
	}

	default int getEnergyStored() {
		return currentME() << 1;
	}

	default int getMaxEnergyStored() {
		return maxME() << 1;
	}
}