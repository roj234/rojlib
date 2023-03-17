package ilib.world.structure.cascading;

import ilib.world.structure.cascading.api.IGenerationData;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/**
 * @author Roj234
 * @since 2020/9/19 23:46
 */
public class GenerationData implements IGenerationData {
	final EnumFacing direction;
	final BlockPos pos;
	final String group;

	public GenerationData(EnumFacing direction, BlockPos pos, String group) {
		this.direction = direction;
		this.pos = pos;
		this.group = group;
	}

	@Override
	public EnumFacing getDirection() {
		return direction;
	}

	@Override
	public BlockPos getPos() {
		return pos;
	}

	@Override
	public String getNextGroup() {
		return group;
	}
}
