package ilib.api.energy;

import net.minecraft.util.EnumFacing;

/**
 * @author Roj234
 */
public interface MEStorage extends METile {
	default METile.EnergyType getEnergyType() {
		return METile.EnergyType.STORAGE;
	}

	int getEnergyFlow(EnumFacing side); // 1 out, 0 no, -1 in
}