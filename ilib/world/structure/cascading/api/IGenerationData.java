package ilib.world.structure.cascading.api;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2020/9/19 23:09
 */
public interface IGenerationData {
	EnumFacing getDirection();

	BlockPos getPos();

	String getNextGroup();
}
