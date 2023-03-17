package ilib.world.schematic;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public final class SchematicToWrite extends Schematic {
	NBTTagList entityTags, tileTags;

	public SchematicToWrite(short width, short height, short length) {
		super(width, height, length);
		this.blocks = new IBlockState[width * height * length];
	}

	public void readFrom(World world, BlockPos start, boolean doEntities) {
		this.entityTags = new NBTTagList();
		this.tileTags = new NBTTagList();

		int endX = start.getX() + width;
		int endY = start.getY() + height;
		int endZ = start.getZ() + length;

		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = start.getY(); y < endY; y++) {
			for (int z = start.getZ(); z < endZ; z++) {
				for (int x = start.getX(); x < endX; x++) {
					IBlockState state = world.getBlockState(pos.setPos(x, y, z));

					int idx = getIndexFromCoordinates(x - start.getX(), y - start.getY(), z - start.getZ());

					blocks[idx] = state;

					TileEntity te = world.getTileEntity(pos);
					if (te != null) {
						NBTTagCompound tag = te.writeToNBT(new NBTTagCompound());
						tag.setShort("x", (short) (x - start.getX()));
						tag.setShort("y", (short) (y - start.getY()));
						tag.setShort("z", (short) (z - start.getZ()));
						tileTags.appendTag(tag);
					}
				}
			}
		}

		if (doEntities) {
			world.getEntitiesWithinAABB(Entity.class, new AxisAlignedBB(start.getX(), start.getY(), start.getZ(), endX, endY, endZ), (entity) -> {
				if (!(entity instanceof EntityPlayer)) {
					NBTTagCompound tag = new NBTTagCompound();
					if (entity.writeToNBTAtomically(tag)) {
						tag.setInteger("x", tag.getInteger("x") - start.getX());
						tag.setInteger("y", tag.getInteger("y") - start.getY());
						tag.setInteger("z", tag.getInteger("z") - start.getZ());
						this.entityTags.appendTag(tag);
					}
				}
				return false;
			});
		}
	}
}
