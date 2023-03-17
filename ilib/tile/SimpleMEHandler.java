package ilib.tile;

import ilib.api.energy.METile;
import ilib.capabilities.MEImpl;

import net.minecraft.util.EnumFacing;

/**
 * @author Roj233
 * @since 2022/4/15 23:58
 */
public class SimpleMEHandler extends MEImpl implements METile {
	private final EnergyType type;

	public SimpleMEHandler(EnergyType type) {
		this.type = type;
	}

	@Override
	public EnergyType getEnergyType() {
		return type;
	}

	@Override
	public boolean canConnectEnergy(EnumFacing from) {
		return true;
	}
}
