package ilib.api.energy;

import net.minecraft.util.EnumFacing;

// ME unit define
public interface METile extends MEItem {
	boolean canConnectEnergy(EnumFacing from);
}