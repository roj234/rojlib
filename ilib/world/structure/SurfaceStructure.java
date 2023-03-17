package ilib.world.structure;

import ilib.util.BlockHelper;
import ilib.world.schematic.Schematic;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class SurfaceStructure extends Structure {
	public SurfaceStructure(Schematic schematic) {
		super(schematic);
	}

	public void generate(World world, BlockPos loc) {
		generate(world, loc.getX(), getYCoord(world, loc), loc.getZ(), F_REPLACE_AIR | F_SPAWN_ENTITY);
	}

	protected int getYCoord(World world, BlockPos pos) {
		return BlockHelper.getSurfaceBlockY(world, pos.getX(), pos.getZ());
	}
}