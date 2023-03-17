package ilib.world.structure;

import ilib.ImpLib;
import ilib.api.world.IPrimeGenerator;
import ilib.math.Section;
import ilib.util.BlockHelper;
import ilib.world.schematic.Schematic;
import roj.math.Rect3i;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos.PooledMutableBlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimer;

/**
 * @author Roj234
 * @since 2020/8/24 12:06
 */
public final class PrimerStructure implements IPrimeGenerator {
	private Schematic schematic;
	private int flag;
	private final Section box = new Section();
	private final Section chunkBox = new Section();

	public PrimerStructure(Schematic s, int x, int y, int z) {
		init(s, x, y, z);
	}

	public PrimerStructure init(Schematic s, int x, int y, int z) {
		this.schematic = s;
		this.box.set(x, y, z, x + s.width(), y + s.height(), z + s.length());
		return this;
	}

	public boolean placeBlock(ChunkPrimer c, int chunkX, int chunkZ) {
		Rect3i sect = box.intersectsWith(chunkBox.set(chunkX, 0, chunkZ, chunkX + 16, 255, chunkZ + 16));

		if (sect != null && sect.volume() > 1) {
			for (int cy = 0, sy = sect.ymin; sy < sect.ymax; cy++, sy++) {
				if (cy > 255) break;
				for (int sx = sect.xmin, cx = sx - chunkX; sx < sect.xmax; sx++, cx++) {
					for (int sz = sect.zmin, cz = sz - chunkZ; sz < sect.zmax; cz++, sz++) {
						IBlockState state = schematic.getBlockState(sx, sy, sz);
						if (state != null) {
							c.setBlockState(cx, cy, cz, state);
						} else if ((flag & Structure.F_REPLACE_AIR) != 0) {
							c.setBlockState(cx, cy, cz, BlockHelper.AIR_STATE);
						}
					}
				}
			}
			return true;
		}
		return false;
	}

	public void writeTileData(World world, int chunkX, int chunkZ) {
		Chunk c = world.getChunk(chunkX, chunkZ);

		Rect3i sect = box.intersectsWith(chunkBox.set(chunkX, 0, chunkZ, chunkX + 16, 255, chunkZ + 16));

		if (sect != null && sect.volume() > 1) {
			PooledMutableBlockPos pos = PooledMutableBlockPos.retain();

			for (int cy = 0, sy = sect.ymin; sy < sect.ymax; cy++, sy++) {
				if (cy > 255) break;
				for (int sx = sect.xmin, cx = sx - chunkX; sx < sect.xmax; sx++, cx++) {
					for (int sz = sect.zmin, cz = sz - chunkZ; sz < sect.zmax; cz++, sz++) {
						IBlockState state = schematic.getBlockState(sx, sy, sz);
						if (state != null) {
							NBTTagCompound tag = schematic.getTileData(sx, sy, sz, cx, cy, cz);
							if (tag != null) {
								TileEntity tile = c.getTileEntity(pos.setPos(cx, cy, cz), Chunk.EnumCreateEntityType.IMMEDIATE);
								if (tile == null) {
									ImpLib.logger().warn("Not a tile at " + sx + ',' + sy + ',' + sz + ", tag: " + tag);
									continue;
								}
								tile.readFromNBT(tag);
								world.scheduleBlockUpdate(pos, state.getBlock(), 2, 0);
							}
						}
					}
				}
			}

			pos.release();
		}
	}

	@Override
	public void generate(World world, int chunkX, int chunkZ, ChunkPrimer chunk) {
		placeBlock(chunk, chunkX << 4, chunkZ << 4);
	}
}
