package ilib.asm.nx;

import ilib.misc.MCHooks;
import ilib.util.PlayerUtil;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.crash.CrashReport;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ReportedException;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import net.minecraftforge.fml.common.FMLLog;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
@Nixim("/")
//!!AT ["net.minecraft.world.chunk.Chunk", ["func_180700_a"], true]
public abstract class NiximChunkLight extends Chunk {
	public NiximChunkLight(World worldIn, int x, int z) {
		super(worldIn, x, z);
	}

	@Shadow
	int queuedLightChecks;
	@Shadow
	ExtendedBlockStorage[] storageArrays;
	@Shadow
	World world;
	@Shadow
	boolean isTerrainPopulated;
	@Shadow
	boolean isLightPopulated;
	@Shadow
	boolean dirty;
	@Shadow
	int heightMapMinimum;
	@Shadow
	int[] precipitationHeightMap;
	@Shadow
	int[] heightMap;
	@Shadow
	boolean[] updateSkylightColumns;
	@Shadow
	boolean isGapLightingUpdated;
	@Shadow
	boolean loaded;

	@Shadow
	private boolean checkLight(int x, int z) {
		return false;
	}

	@Shadow
	private void setSkylightUpdated() {}

	@Inject
	public void checkLight() {
		int depth = MCHooks.getStackDepth();
		if (depth > 100) {
			if (depth > 120) {
				PlayerUtil.broadcastAll("checkLight() StackOverFlow!");
				FMLLog.bigWarning("StackOverFlowError caught!");
				if (depth > 160) {
					throw new ReportedException(new CrashReport("checkLight", new StackOverflowError()));
				}
			}
			return;
		}

		this.isTerrainPopulated = true;
		this.isLightPopulated = true;

		BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain();
		int cx = this.x << 4;
		int cz = this.z << 4;

		if (this.world.provider.hasSkyLight()) {
			if (this.world.isAreaLoaded(pos.setPos(cx - 1, 0, cz - 1), pos.setPos(cx + 16, this.world.getSeaLevel(), cz + 16))) {
				out:
				for (int x = 0; x < 16; ++x) {
					for (int z = 0; z < 16; ++z) {
						if (!this.checkLight(x, z)) {
							this.isLightPopulated = false;
							break out;
						}
					}
				}

				if (this.isLightPopulated) {
					EnumFacing[] facings = EnumFacing.VALUES;

					for (int i = 2; i < 6; i++) {
						EnumFacing facing = facings[i];
						int dist = facing.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE ? 16 : 1;
						pos.setPos(cx, 0, cz).move(facing, dist);
						if (this.world.isChunkGeneratedAt(pos.getX() >> 4, pos.getZ() >> 4)) {
							this.world.getChunk(pos.getX() >> 4, pos.getZ() >> 4).checkLightSide(facing.getOpposite());
						}
					}

					this.setSkylightUpdated();
				}
			} else {
				this.isLightPopulated = false;
			}
		}

		pos.release();

	}

	@Inject
	public void generateSkylightMap() {
		int i = this.getTopFilledSegment();
		this.heightMapMinimum = 2147483647;

		BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain();

		for (int x = 0; x < 16; ++x) {
			for (int z = 0; z < 16; ++z) {
				this.precipitationHeightMap[x + (z << 4)] = -999;

				for (int y = i + 16; y > 0; --y) {
					if (this.getBlockLightOpacity(x, y - 1, z) != 0) {
						this.heightMap[z << 4 | x] = y;
						if (y < this.heightMapMinimum) {
							this.heightMapMinimum = y;
						}
						break;
					}
				}

				if (this.world.provider.hasSkyLight()) {
					int light = 15;
					int y = i + 16 - 1;

					do {
						int opacity = this.getBlockLightOpacity(x, y, z);
						if (opacity == 0 && light != 15) {
							opacity = 1;
						}

						light -= opacity;
						if (light > 0) {
							ExtendedBlockStorage ebs = this.storageArrays[y >> 4];
							if (ebs != NULL_BLOCK_STORAGE) {
								ebs.setSkyLight(x, y & 15, z, light);
								this.world.notifyLightSet(pos.setPos((this.x << 4) + x, y, (this.z << 4) + z));
							}
						}

						--y;
					} while (y > 0 && light > 0);
				}
			}
		}

		pos.release();

		this.dirty = true;
	}

	@Inject
	public void checkLightSide(EnumFacing facing) {
		if (this.isTerrainPopulated) {
			int l;
			switch (facing.ordinal()) {
				case 5: {
					for (l = 0; l < 16; ++l) {
						this.checkLight(15, l);
					}
				}
				break;
				case 4: {
					for (l = 0; l < 16; ++l) {
						this.checkLight(0, l);
					}
				}
				break;
				case 3: {
					for (l = 0; l < 16; ++l) {
						this.checkLight(l, 15);
					}
				}
				break;
				case 2: {
					for (l = 0; l < 16; ++l) {
						this.checkLight(l, 0);
					}
				}
				break;
			}
		}
	}

	@Inject
	private void recheckGaps(boolean onlyOne) {
		this.world.profiler.startSection("recheckGaps");
		if (this.world.isAreaLoaded(new BlockPos(this.x * 16 + 8, 0, this.z * 16 + 8), 16)) {
			for (int dx = 0; dx < 16; ++dx) {
				for (int dz = 0; dz < 16; ++dz) {
					if (this.updateSkylightColumns[dx + dz * 16]) {
						this.updateSkylightColumns[dx + dz * 16] = false;
						int y = this.getHeightValue(dx, dz);
						int x = (this.x << 4) + dx;
						int z = (this.z << 4) + dz;
						int minY = 2147483647;

						EnumFacing[] facings = EnumFacing.VALUES;

						for (int i = 2; i < 6; i++) {
							EnumFacing facing = facings[i];
							minY = Math.min(minY, this.world.getChunksLowestHorizon(x + facing.getXOffset(), z + facing.getZOffset()));
						}

						this.checkSkylightNeighborHeight(x, z, minY);

						for (int i = 2; i < 6; i++) {
							EnumFacing facing = facings[i];
							this.checkSkylightNeighborHeight(x + facing.getXOffset(), z + facing.getZOffset(), y);
						}

						if (onlyOne) {
							this.world.profiler.endSection();
							return;
						}
					}
				}
			}

			this.isGapLightingUpdated = false;
		}

		this.world.profiler.endSection();
	}

	@Inject
	private void checkSkylightNeighborHeight(int x, int z, int maxValue) {
		int y = this.world.getHeight(x, z);
		if (y > maxValue) {
			this.updateSkylightNeighborHeight(x, z, maxValue, y + 1);
		} else if (y < maxValue) {
			this.updateSkylightNeighborHeight(x, z, y, maxValue + 1);
		}
	}

	@Inject
	private void updateSkylightNeighborHeight(int x, int z, int startY, int endY) {
		BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain(x, 0, z);
		if (endY > startY && this.world.isAreaLoaded(pos, 16)) {
			for (int y = startY; y < endY; ++y) {
				this.world.checkLightFor(EnumSkyBlock.SKY, pos.setPos(x, y, z));
			}

			this.dirty = true;
		}
		pos.release();
	}

	@Inject
	private void relightBlock(int x, int y, int z) {
		int cachedY = this.heightMap[z << 4 | x] & 255;
		int y1 = cachedY;
		if (y > cachedY) {
			y1 = y;
		}

		while (y1 > 0 && this.getBlockLightOpacity(x, y1 - 1, z) == 0) {
			--y1;
		}

		BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain();

		if (y1 != cachedY) {
			this.world.markBlocksDirtyVertical(x + this.x * 16, z + this.z * 16, y1, cachedY);
			this.heightMap[z << 4 | x] = y1;
			int rx = (this.x << 4) + x;
			int rz = (this.z << 4) + z;

			int y2;
			int startY;

			if (this.world.provider.hasSkyLight()) {
				ExtendedBlockStorage storage;
				if (y1 < cachedY) {
					for (y2 = y1; y2 < cachedY; ++y2) {
						storage = this.storageArrays[y2 >> 4];
						if (storage != NULL_BLOCK_STORAGE) {
							storage.setSkyLight(x, y2 & 15, z, 15);
							this.world.notifyLightSet(pos.setPos(rx, y2, rz));
						}
					}
				} else {
					for (y2 = cachedY; y2 < y1; ++y2) {
						storage = this.storageArrays[y2 >> 4];
						if (storage != NULL_BLOCK_STORAGE) {
							storage.setSkyLight(x, y2 & 15, z, 0);
							this.world.notifyLightSet(pos.setPos(rx, y2, rz));
						}
					}
				}

				y2 = 15;

				while (y1 > 0 && y2 > 0) {
					--y1;
					startY = this.getBlockLightOpacity(x, y1, z);
					if (startY == 0) {
						startY = 1;
					}

					y2 -= startY;
					if (y2 < 0) {
						y2 = 0;
					}

					ExtendedBlockStorage storage1 = this.storageArrays[y1 >> 4];
					if (storage1 != NULL_BLOCK_STORAGE) {
						storage1.setSkyLight(x, y1 & 15, z, y2);
					}
				}
			}

			y2 = this.heightMap[z << 4 | x];
			startY = cachedY;
			int endY = y2;
			if (y2 < cachedY) {
				startY = y2;
				endY = cachedY;
			}

			if (y2 < this.heightMapMinimum) {
				this.heightMapMinimum = y2;
			}

			if (this.world.provider.hasSkyLight()) {
				EnumFacing[] facings = EnumFacing.VALUES;

				for (int i = 2; i < 6; i++) {
					EnumFacing facing = facings[i];
					this.updateSkylightNeighborHeight(rx + facing.getXOffset(), rz + facing.getZOffset(), startY, endY);
				}

				this.updateSkylightNeighborHeight(rx, rz, startY, endY);
			}

			this.dirty = true;
		}

		pos.release();

	}

	@Inject
	public BlockPos getPrecipitationHeight(BlockPos pos) {
		int dx = pos.getX() & 15;
		int dz = pos.getZ() & 15;
		int i = dx | dz << 4;


		BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain(pos.getX(), this.precipitationHeightMap[i], pos.getZ());

		if (pos1.getY() == -999) {
			pos1.setY(this.getTopFilledSegment() + 15);
			int height = -1;

			while (pos1.getY() > 0 && height == -1) {
				IBlockState state = this.getBlockState(pos1);
				Material material = state.getMaterial();
				if (!material.blocksMovement() && !material.isLiquid()) {
					pos1.move(EnumFacing.DOWN);
				} else {
					height = pos1.getY() + 1;
				}
			}

			this.precipitationHeightMap[i] = height;
		}

		pos1.release();

		return new BlockPos(pos.getX(), this.precipitationHeightMap[i], pos.getZ());
	}

	@Inject
	private int getBlockLightOpacity(int x, int y, int z) {
		IBlockState state = this.getBlockState(x, y, z);
		if (!this.loaded) {
			return state.getLightOpacity();
		} else {
			BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain(this.x << 4 | x & 15, y, this.z << 4 | z & 15);
			int i = state.getLightOpacity(this.world, pos);
			pos.release();
			return i;
		}
	}

	@Inject
	public void enqueueRelightChecks() {
		if (this.queuedLightChecks < 4096) {
			BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain();

			for (int i = 0; i < 8; ++i) {
				if (this.queuedLightChecks >= 4096) {
					return;
				}

				int y = this.queuedLightChecks % 16;
				int x = this.queuedLightChecks / 16 % 16;
				int z = this.queuedLightChecks / 256;
				++this.queuedLightChecks;


				int cx = this.x << 4;
				int cz = this.z << 4;

				for (int yOff = 0; yOff < 16; ++yOff) {
					boolean isCorner = yOff == 0 || yOff == 15 || x == 0 || x == 15 || z == 0 || z == 15;

					boolean flag = false;

					final ExtendedBlockStorage storage = this.storageArrays[y];

					pos.setPos(cx + x, (y << 4) + yOff, cz + z);

					if (storage != NULL_BLOCK_STORAGE) {
						final IBlockState state = storage.get(x, yOff, z);

						if (state.getBlock().isAir(state, this.world, pos)) {
							isCorner = true;
						}
					}

					if (isCorner) {
						int ox = pos.getX();
						int oy = pos.getY();
						int oz = pos.getZ();

						EnumFacing[] v = EnumFacing.VALUES;

						for (EnumFacing facing : v) {
							if (this.world.getBlockState(pos.setPos(ox, oy, oz).move(facing)).getLightValue(this.world, pos) > 0) {
								this.world.checkLight(pos);
							}
						}

						this.world.checkLight(pos.setPos(ox, oy, oz));
					}
				}
			}
		}

	}

}