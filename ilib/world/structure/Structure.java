package ilib.world.structure;

import ilib.ImpLib;
import ilib.util.BlockHelper;
import ilib.util.EntityHelper;
import ilib.world.schematic.Schematic;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.PooledMutableBlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class Structure {
	public static final int F_REPLACE_AIR = 1, F_SPAWN_ENTITY = 2;

	protected Schematic schematic;

	public Structure(Schematic schematic) {
		this.schematic = schematic;
	}

	public void generate(World world, BlockPos loc, int flag) {
		generate(world, loc.getX(), loc.getY(), loc.getZ(), flag);
	}

	public void generate(World world, int xCoord, int yCoord, int zCoord, int flag) {
		if (world.isRemote) throw new IllegalStateException("world.isRemote");

		Schematic schem = this.getSchematic();

		WorldServer ws = (WorldServer) world;
		PlayerChunkMap map = ws.getPlayerChunkMap();
		BlockPos.PooledMutableBlockPos wPos = PooledMutableBlockPos.retain();
		for (int x = 0; x < schem.width(); x++) {
			for (int y = 0; y < schem.height(); y++) {
				for (int z = 0; z < schem.length(); ++z) {
					int worldX = xCoord + x;
					int worldY = yCoord + y;
					int worldZ = zCoord + z;

					IBlockState state = schem.getBlockState(x, y, z);
					if (state != null) {
						Block block = state.getBlock();
						if (!block.isAir(state, ws, wPos.setPos(worldX, worldY, worldZ))) {
							ws.getChunk(wPos).setBlockState(wPos, state);
							if (block.hasTileEntity(state)) {
								NBTTagCompound tag = schem.getTileData(x, y, z, worldX, worldY, worldZ);
								if (tag != null) {
									TileEntity tile = ws.getTileEntity(wPos);
									if (tile == null) {
										ImpLib.logger().warn("Not a tile at " + worldX + ',' + worldY + ',' + worldZ + ", tag: " + tag);
										continue;
									}
									tile.readFromNBT(tag);
									BlockHelper.updateBlock(ws, wPos);
								}
							}
						} else if ((flag & F_REPLACE_AIR) != 0 && !ws.isAirBlock(wPos)) {
							ws.getChunk(wPos).setBlockState(wPos, BlockHelper.AIR_STATE);
							ws.removeTileEntity(wPos);
						}
						map.markBlockForUpdate(wPos);
					}
				}
			}
		}

		for (int x = 0; x < schem.width(); x++) {
			for (int y = 0; y < schem.height(); y++) {
				for (int z = 0; z < schem.length(); ++z) {
					IBlockState state = schem.getBlockState(x, y, z);
					if (state != null) {
						int worldX = xCoord + x;
						int worldY = yCoord + y;
						int worldZ = zCoord + z;

						ws.checkLight(wPos.setPos(worldX, worldY, worldZ));
					}
				}
			}
		}

		wPos.release();

		if ((flag & F_SPAWN_ENTITY) != 0) {
			List<NBTTagCompound> entities = schem.getEntities();
			for (int i = 0; i < entities.size(); i++) {
				NBTTagCompound tag = entities.get(i);
				EntityHelper.spawnEntityFromTag(tag, ws, tag.getInteger("x") + xCoord, tag.getInteger("y") + yCoord, tag.getInteger("z") + zCoord);
			}
		}
	}

	@SuppressWarnings("unchecked")
	public <T extends Structure> T setSchematic(Schematic schematic) {
		this.schematic = schematic;
		return (T) this;
	}

	public Schematic getSchematic() {
		return this.schematic;
	}
}
