package ilib.grid;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;

import javax.annotation.Nullable;

/**
 * @author Roj233
 * @since 2022/5/15 3:30
 */
public class MyChunkCache implements IBlockAccess {
	protected int x, z;
	protected Chunk[][] chunks;
	protected World world;
	protected int chunkRemain;

	public boolean init(World world, int beginX, int beginY, int beginZ, int endX, int endY, int endZ) {
		this.world = world;

		x = beginX >> 4;
		z = beginZ >> 4;
		int x1 = endX >> 4;
		int z1 = endZ >> 4;
		chunks = new Chunk[x1 - x + 1][z1 - z + 1];

		boolean empty = true;
		int x2;
		int z2;
		for (x2 = x; x2 <= x1; ++x2) {
			for (z2 = z; z2 <= z1; ++z2) {
				ChunkProviderServer cps = (ChunkProviderServer) world.getChunkProvider();
				Chunk c = cps.getLoadedChunk(x2, z2);
				if (c == null) {
					if (cps.isChunkGeneratedAt(x2, z2)) {
						chunkRemain++;

						int finalX = x2;
						int finalZ = z2;
						cps.loadChunk(x2, z2, () -> {
							Chunk c1 = cps.getLoadedChunk(finalX, finalZ);
							chunks[finalX - x][finalZ - z] = c1;
							chunkRemain--;
						});
					}
				}
				chunks[x2 - x][z2 - z] = c;
				if (c != null && !c.isEmptyBetween(beginY, endY)) {
					empty = false;
				}
			}
		}

		return empty && chunkRemain == 0;
	}

	public boolean isDone() {
		return chunkRemain == 0;
	}

	@Nullable
	public TileEntity getTileEntity(BlockPos pos) {
		return getTileEntity(pos, Chunk.EnumCreateEntityType.CHECK);
	}

	@Nullable
	public TileEntity getTileEntity(BlockPos pos, Chunk.EnumCreateEntityType createType) {
		int i = (pos.getX() >> 4) - x;
		int j = (pos.getZ() >> 4) - z;
		return !withinBounds(i, j) ? null : chunks[i][j].getTileEntity(pos, createType);
	}

	public int getCombinedLight(BlockPos pos, int lightValue) {
		return 0;
	}

	public IBlockState getBlockState(BlockPos pos) {
		if (pos.getY() >= 0 && pos.getY() < 256) {
			int i = (pos.getX() >> 4) - x;
			int j = (pos.getZ() >> 4) - z;
			Chunk[][] cs = this.chunks;
			if (i >= 0 && i < cs.length && j >= 0 && j < cs[0].length) {
				Chunk chunk = cs[i][j];
				if (chunk != null) return chunk.getBlockState(pos);
			}
		}

		return Blocks.AIR.getDefaultState();
	}

	public Biome getBiome(BlockPos pos) {
		int i = (pos.getX() >> 4) - x;
		int j = (pos.getZ() >> 4) - z;
		return !withinBounds(i, j) ? Biomes.PLAINS : chunks[i][j].getBiome(pos, world.getBiomeProvider());
	}

	public boolean isAirBlock(BlockPos pos) {
		IBlockState state = getBlockState(pos);
		return state.getBlock().isAir(state, this, pos);
	}

	public int getStrongPower(BlockPos pos, EnumFacing direction) {
		return getBlockState(pos).getStrongPower(this, pos, direction);
	}

	public WorldType getWorldType() {
		return world.getWorldType();
	}

	public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean def) {
		int x = (pos.getX() >> 4) - this.x;
		int z = (pos.getZ() >> 4) - this.z;
		if (pos.getY() >= 0 && pos.getY() < 256) {
			if (withinBounds(x, z)) {
				IBlockState state = getBlockState(pos);
				return state.getBlock().isSideSolid(state, this, pos, side);
			}
		}
		return def;
	}

	private boolean withinBounds(int x, int z) {
		Chunk[][] c = chunks;
		return x >= 0 && x < c.length && z >= 0 && z < c[0].length && c[x][z] != null;
	}
}