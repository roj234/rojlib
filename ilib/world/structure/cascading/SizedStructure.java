package ilib.world.structure.cascading;

import ilib.math.Section;
import ilib.world.schematic.Schematic;
import ilib.world.structure.Structure;
import ilib.world.structure.cascading.api.IStructure;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class SizedStructure extends Structure implements IStructure {
	public SizedStructure(@Nonnull Schematic schematic) {
		super(schematic);
	}

	@Override
	public void generate(@Nonnull World world, @Nonnull GenerateContext context) {
		BlockPos pos = context.getCurrPos();
		generate(world, pos, Structure.F_REPLACE_AIR | Structure.F_SPAWN_ENTITY);
	}

	@Override
	@Nonnull
	public Section getSection(@Nonnull BlockPos offset) {
		return new Section(offset.getX(), offset.getY(), offset.getZ(), offset.getX() + schematic.width(), offset.getY() + schematic.height(), offset.getZ() + schematic.length());
	}
}