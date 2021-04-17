package ilib.asm.nx;

import ilib.Config;
import ilib.ImpLib;
import ilib.misc.MCHooks;
import ilib.util.Reflection;
import roj.asm.nixim.*;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.init.Blocks;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ReportedException;
import net.minecraft.util.math.*;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;

import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
@Nixim("/")
abstract class NxCachedPos extends World {
	protected NxCachedPos(ISaveHandler p_i45749_1_, WorldInfo p_i45749_2_, WorldProvider p_i45749_3_, Profiler p_i45749_4_, boolean p_i45749_5_) {
		super(p_i45749_1_, p_i45749_2_, p_i45749_3_, p_i45749_4_, p_i45749_5_);
	}

	@Shadow
	int[] lightUpdateBlockList;

	@Shadow
	private boolean isWater(BlockPos pos) {
		return false;
	}

	@Shadow
	private boolean isAreaLoaded(int xStart, int yStart, int zStart, int xEnd, int yEnd, int zEnd, boolean allowEmpty) {
		return false;
	}

	@Shadow
	private int getRawLight(BlockPos pos, EnumSkyBlock lightType) {
		return 0;
	}

	@Shadow
	int skylightSubtracted;

	@Nullable
	@Override
	@Dynamic({"coremod_flag","64"})
	public RayTraceResult rayTraceBlocks(Vec3d begin, Vec3d end, boolean stopOnLiquid, boolean ignoreBlockWithoutBoundingBox, boolean returnLastUncollidableBlock) {
		if (!Double.isNaN(begin.x) && !Double.isNaN(begin.y) && !Double.isNaN(begin.z)) {
			if (!Double.isNaN(end.x) && !Double.isNaN(end.y) && !Double.isNaN(end.z)) {
				BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain();

				int i = MathHelper.floor(end.x);
				int j = MathHelper.floor(end.y);
				int k = MathHelper.floor(end.z);
				int x = MathHelper.floor(begin.x);
				int y = MathHelper.floor(begin.y);
				int z = MathHelper.floor(begin.z);
				pos.setPos(x, y, z);
				IBlockState state = this.getBlockState(pos);
				Block block = state.getBlock();
				RayTraceResult result;
				if ((!ignoreBlockWithoutBoundingBox || state.getCollisionBoundingBox(this, pos) != Block.NULL_AABB) && block.canCollideCheck(state, stopOnLiquid)) {
					result = state.collisionRayTrace(this, pos, begin, end);
					if (result != null) {
						pos.release();
						return result;
					}
				}

				result = null;
				int maxDist1 = 200;

				begin = new Vec3d(begin.x, begin.y, begin.z);
				while (maxDist1-- >= 0) {
					if (Double.isNaN(begin.x) || Double.isNaN(begin.y) || Double.isNaN(begin.z)) {
						return null;
					}

					if (x == i && y == j && z == k) {
						return result;
					}

					boolean flag2 = true;
					boolean flag = true;
					boolean flag1 = true;
					double d0 = 999.0D;
					double d1 = 999.0D;
					double d2 = 999.0D;
					if (i > x) {
						d0 = x + 1.0D;
					} else if (i < x) {
						d0 = x + 0.0D;
					} else {
						flag2 = false;
					}

					if (j > y) {
						d1 = y + 1.0D;
					} else if (j < y) {
						d1 = y + 0.0D;
					} else {
						flag = false;
					}

					if (k > z) {
						d2 = z + 1.0D;
					} else if (k < z) {
						d2 = z + 0.0D;
					} else {
						flag1 = false;
					}

					double d3 = 999.0D;
					double d4 = 999.0D;
					double d5 = 999.0D;
					double d6 = end.x - begin.x;
					double d7 = end.y - begin.y;
					double d8 = end.z - begin.z;
					if (flag2) {
						d3 = (d0 - begin.x) / d6;
					}

					if (flag) {
						d4 = (d1 - begin.y) / d7;
					}

					if (flag1) {
						d5 = (d2 - begin.z) / d8;
					}

					if (d3 == -0.0D) {
						d3 = -1.0E-4D;
					}

					if (d4 == -0.0D) {
						d4 = -1.0E-4D;
					}

					if (d5 == -0.0D) {
						d5 = -1.0E-4D;
					}

					EnumFacing face;
					if (d3 < d4 && d3 < d5) {
						face = i > x ? EnumFacing.WEST : EnumFacing.EAST;
						Reflection.setVec(begin, d0, begin.y + d7 * d3, begin.z + d8 * d3);
					} else if (d4 < d5) {
						face = j > y ? EnumFacing.DOWN : EnumFacing.UP;
						Reflection.setVec(begin, begin.x + d6 * d4, d1, begin.z + d8 * d4);
					} else {
						face = k > z ? EnumFacing.NORTH : EnumFacing.SOUTH;
						Reflection.setVec(begin, begin.x + d6 * d5, begin.y + d7 * d5, d2);
					}

					x = MathHelper.floor(begin.x) - (face == EnumFacing.EAST ? 1 : 0);
					y = MathHelper.floor(begin.y) - (face == EnumFacing.UP ? 1 : 0);
					z = MathHelper.floor(begin.z) - (face == EnumFacing.SOUTH ? 1 : 0);
					pos.setPos(x, y, z);
					state = this.getBlockState(pos);
					block = state.getBlock();
					if (!ignoreBlockWithoutBoundingBox || state.getMaterial() == Material.PORTAL || state.getCollisionBoundingBox(this, pos) != Block.NULL_AABB) {
						if (block.canCollideCheck(state, stopOnLiquid)) {
							RayTraceResult r = state.collisionRayTrace(this, pos, begin, end);
							if (r != null) {
								pos.release();
								return r;
							}
						} else if (returnLastUncollidableBlock) {
							result = new RayTraceResult(RayTraceResult.Type.MISS, begin, face, pos);
						}
					}
				}

				if (result == null) pos.release();
				return result;
			} else {
				return null;
			}
		} else {
			return null;
		}
	}

	@Inject("/")
	public int getLight(BlockPos pos, boolean checkNeighbors) {
		if (pos.getX() >= -30000000 && pos.getZ() >= -30000000 && pos.getX() < 30000000 && pos.getZ() < 30000000) {
			BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain();
			if (checkNeighbors && this.getBlockState(pos).useNeighborBrightness()) {
				EnumFacing[] values = EnumFacing.VALUES;
				int light = 0;
				for (int i = 1; i < values.length; i++) {
					light = Math.max(light, this.getLight(pos1.setPos(pos).move(values[i]), false));
					if (light >= 15) {
						pos1.release();
						return light;
					}
				}
				pos1.release();
				return light;
			} else if (pos.getY() < 0) {
				pos1.release();
				return 0;
			} else {
				if (pos1.setPos(pos).getY() >= 256) {
					pos1.setY(255);
				}

				Chunk chunk = this.getChunk(pos1);
				int l = chunk.getLightSubtracted(pos1, this.skylightSubtracted);
				pos1.release();
				return l;
			}
		} else {
			return 15;
		}
	}

	@Inject("/")
	public int getStrongPower(BlockPos pos) {
		int red = 0;

		BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain();
		for (EnumFacing face : EnumFacing.VALUES) {
			red = Math.max(red, this.getStrongPower(pos1.setPos(pos).move(face), face));
			if (red >= 15) break;
		}
		pos1.release();

		return red;
	}

	@Inject("/")
	public boolean canBlockFreezeBody(BlockPos pos, boolean noWaterAdj) {
		Biome biome = this.getBiome(pos);
		float f = biome.getTemperature(pos);
		if (f < 0.15F) {
			if (pos.getY() >= 0 && pos.getY() < 256 && this.getLightFor(EnumSkyBlock.BLOCK, pos) < 10) {
				BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain(pos);
				IBlockState state = this.getBlockState(pos1);
				Block block = state.getBlock();
				if ((block == Blocks.WATER || block == Blocks.FLOWING_WATER) && state.getValue(BlockLiquid.LEVEL) == 0) {
					if (!noWaterAdj) {
						pos1.release();
						return true;
					}

					boolean flag = this.isWater(pos1.move(EnumFacing.WEST)) && this.isWater(pos1.setPos(pos).move(EnumFacing.EAST)) && this.isWater(
						pos1.setPos(pos).move(EnumFacing.NORTH)) && this.isWater(pos1.setPos(pos).move(EnumFacing.SOUTH));
					pos1.release();
					return !flag;
				}
				pos1.release();
			}
		}
		return false;
	}

	@Inject("/")
	public void updateComparatorOutputLevel(BlockPos pos, Block blockIn) {
		BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain();
		for (EnumFacing face : EnumFacing.VALUES) {
			if (this.isBlockLoaded(pos1.setPos(pos).move(face))) {
				IBlockState state = this.getBlockState(pos1);
				Block block = state.getBlock();

				block.onNeighborChange(this, pos1, pos);
				if (block.isNormalCube(state, this, pos1)) {
					block = this.getBlockState(pos1.move(face)).getBlock();
					if (block.getWeakChanges(this, pos1)) {
						block.onNeighborChange(this, pos1, pos);
					}
				}
			}
		}
		pos1.release();
	}

	@Inject("/")
	public int getRedstonePowerFromNeighbors(BlockPos pos) {
		int cur = 0;

		BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain();
		for (EnumFacing face : EnumFacing.VALUES) {
			cur = Math.max(this.getRedstonePower(pos1.setPos(pos).move(face), face), cur);
			if (cur >= 15) break;
		}

		pos1.release();
		return cur;
	}

	@Inject("/")
	public boolean isBlockPowered(BlockPos pos) {
		BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain();
		for (EnumFacing face : EnumFacing.VALUES) {
			int red = this.getRedstonePower(pos1.setPos(pos).move(face), face);
			if (red > 0) {
				pos1.release();
				return true;
			}
		}
		pos1.release();
		return false;
	}

	@SideOnly(Side.CLIENT)
	@Inject(value = "/", flags = roj.asm.nixim.Inject.FLAG_OPTIONAL)
	public int getLightFromNeighborsFor(EnumSkyBlock type, BlockPos pos) {
		if (!this.provider.hasSkyLight() && type == EnumSkyBlock.SKY) {
			return 0;
		} else {
			if (pos.getY() < 0) {
				pos = new BlockPos(pos.getX(), 0, pos.getZ());
			}

			if (!this.isValid(pos)) {
				return type.defaultLightValue;
			} else if (!this.isBlockLoaded(pos)) {
				return type.defaultLightValue;
			} else if (this.getBlockState(pos).useNeighborBrightness()) {
				BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain();

				EnumFacing[] facings = EnumFacing.VALUES;

				int maxLight = 0;

				for (int i = 1; i < 6; i++) {
					int currLight = getLightFor(type, pos1.setPos(pos).move(facings[i]));
					if (currLight > maxLight) maxLight = currLight;
					if (maxLight >= 15) break;
				}

				pos1.release();
				return maxLight;
			} else {
				Chunk chunk = this.getChunk(pos);
				return chunk.getLightFor(type, pos);
			}
		}
	}

	@Inject("/")
	public void notifyNeighborsOfStateExcept(BlockPos pos, Block blockType, EnumFacing skipSide) {
		EnumSet<EnumFacing> directions = EnumSet.allOf(EnumFacing.class);
		directions.remove(skipSide);
		if (!ForgeEventFactory.onNeighborNotify(this, pos, this.getBlockState(pos), directions, false).isCanceled()) {
			BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain();
			for (EnumFacing facing : MCHooks.REDSTONE_UPDATE_ORDER) {
				if (directions.contains(facing)) {
					this.neighborChanged(pos1.setPos(pos).move(facing), blockType, pos);
				}
			}
			pos1.release();
		}
	}

	@Inject("/")
	public void updateObservingBlocksAt(BlockPos pos, Block blockType) {
		BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain();
		for (EnumFacing facing : MCHooks.REDSTONE_UPDATE_ORDER) {
			this.observedNeighborChanged(pos1.setPos(pos).move(facing), blockType, pos);
		}
		pos1.release();
	}

	@Inject("/")
	public void neighborChanged(BlockPos pos, final Block blockIn, BlockPos fromPos) {
		if (!this.isRemote) {
			int depth = Config.checkStackDepth ? MCHooks.getStackDepth() : 0;
			if (depth > 400) {
				if (depth > 500) {
					CrashReport report = CrashReport.makeCrashReport(new StackOverflowError(), "Exception while updating neighbours");
					CrashReportCategory category = report.makeCategory("Block being updated");
					category.addDetail("Source block type", () -> {
						try {
							return String.format("ID #%d (%s // %s // %s)", Block.getIdFromBlock(blockIn), blockIn.getTranslationKey(), blockIn.getClass().getName(), blockIn.getRegistryName());
						} catch (Throwable var2) {
							return "ID #" + Block.getIdFromBlock(blockIn);
						}
					});
					throw new ReportedException(report);
				}
				ImpLib.logger().warn("循环方块更新警告: 栈深" + depth);
				return;
			}

			IBlockState state = this.getBlockState(pos);

			try {
				state.neighborChanged(this, pos, blockIn, fromPos);
			} catch (Throwable e) {
				CrashReport crashreport = CrashReport.makeCrashReport(e, "Exception while updating neighbours");
				CrashReportCategory crashreportcategory = crashreport.makeCategory("Block being updated");
				crashreportcategory.addDetail("Source block type", () -> {
					try {
						return String.format("ID #%d (%s // %s // %s)", Block.getIdFromBlock(blockIn), blockIn.getTranslationKey(), blockIn.getClass().getName(), blockIn.getRegistryName());
					} catch (Throwable var2) {
						return "ID #" + Block.getIdFromBlock(blockIn);
					}
				});
				CrashReportCategory.addBlockInfo(crashreportcategory, pos, state);
				throw new ReportedException(crashreport);
			}
		}
	}

	@Inject("/")
	public void notifyNeighborsOfStateChange(BlockPos pos, Block blockType, boolean updateObservers) {
		EnumSet<EnumFacing> directions = EnumSet.allOf(EnumFacing.class);
		if (!ForgeEventFactory.onNeighborNotify(this, pos, this.getBlockState(pos), directions, updateObservers).isCanceled()) {
			BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain();
			for (EnumFacing facing : directions) {
				this.neighborChanged(pos1.setPos(pos).move(facing), blockType, pos);
			}
			pos1.release();

			if (updateObservers) {
				this.updateObservingBlocksAt(pos, blockType);
			}
		}
	}

	@Inject("/")
	public void markBlocksDirtyVertical(int x, int z, int y1, int y2) {
		int tmp;
		if (y1 > y2) {
			tmp = y2;
			y2 = y1;
			y1 = tmp;
		}

		BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain(x, 0, z);
		if (this.provider.hasSkyLight()) {
			for (tmp = y1; tmp <= y2; ++tmp) {
				pos1.setY(tmp);
				this.checkLightFor(EnumSkyBlock.SKY, pos1);
			}
		}
		pos1.release();

		this.markBlockRangeForRenderUpdate(x, y1, z, x, y2, z);
	}

	@Inject("/")
	public IBlockState getGroundAboveSeaLevel(BlockPos pos) {
		BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain(pos);
		pos1.setY(getSeaLevel());

		while (!this.isAirBlock(pos1)) {
			pos1.setY(pos1.getY() + 1);
		}
		pos1.setY(pos1.getY() - 1);

		IBlockState state = this.getBlockState(pos1);
		pos1.release();
		return state;
	}

	@Inject("/")
	public boolean canBlockSeeSky(BlockPos pos) {
		if (pos.getY() >= this.getSeaLevel()) {
			return this.canSeeSky(pos);
		} else {
			BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain(pos);
			pos1.setY(getSeaLevel());

			BlockPos pos2 = pos1.toImmutable();

			if (!this.canSeeSky(pos2)) {
				pos1.release();
				return false;
			} else {
				while (pos1.getY() > pos.getY()) {
					IBlockState state = this.getBlockState(pos1.move(EnumFacing.DOWN));
					if (state.getBlock().getLightOpacity(state, this, pos2) > 0 && !state.getMaterial().isLiquid()) {
						pos1.release();
						return false;
					}
				}

				pos1.release();
				return true;
			}
		}
	}

	@Inject("/")
	public BlockPos getTopSolidOrLiquidBlock(BlockPos pos) {
		Chunk chunk = this.getChunk(pos);


		BlockPos.PooledMutableBlockPos pos1 = BlockPos.PooledMutableBlockPos.retain(pos);
		pos1.setY(chunk.getTopFilledSegment() + 16);

		BlockPos imm;

		while (pos1.getY() >= 0) {
			IBlockState state = getBlockState(pos1.move(EnumFacing.DOWN));
			if (state.getMaterial().blocksMovement() && !state.getBlock().isLeaves(state, this, pos1) && !state.getBlock().isFoliage(this, pos1)) {
				imm = pos1.move(EnumFacing.UP).toImmutable();
				pos1.release();
				return imm;
			}
		}

		imm = pos1.toImmutable();
		pos1.release();

		return imm;
	}

	@Inject("/")
	public boolean isFlammableWithin(AxisAlignedBB bb) {
		int xMin = MathHelper.floor(bb.minX);
		int xMax = MathHelper.ceil(bb.maxX);
		int yMin = MathHelper.floor(bb.minY);
		int yMax = MathHelper.ceil(bb.maxY);
		int zMin = MathHelper.floor(bb.minZ);
		int zMax = MathHelper.ceil(bb.maxZ);
		if (this.isAreaLoaded(xMin, yMin, zMin, xMax, yMax, zMax, true)) {
			BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain();

			for (int x = xMin; x < xMax; x++) {
				for (int y = yMin; y < yMax; ++y) {
					for (int z = zMin; z < zMax; ++z) {
						Block block = this.getBlockState(pos.setPos(x, y, z)).getBlock();
						if (block == Blocks.FIRE || block == Blocks.FLOWING_LAVA || block == Blocks.LAVA) {
							pos.release();
							return true;
						}

						if (block.isBurning(this, pos)) {
							pos.release();
							return true;
						}
					}
				}
			}

			pos.release();
		}
		return false;
	}

	@Inject("/")
	public boolean checkLightFor(EnumSkyBlock lightType, BlockPos pos) {
		if (!this.isAreaLoaded(pos, 16, false)) {
			return false;
		} else {
			int updateRange = this.isAreaLoaded(pos, 18, false) ? 17 : 15;

			this.profiler.startSection("getBrightness");

			int light = this.getLightFor(lightType, pos);
			int rawLight = this.getRawLight(pos, lightType);

			int x = pos.getX();
			int y = pos.getY();
			int z = pos.getZ();

			int bufferIndex = 0;

			int x1, y1, z1;
			int realLight;
			int dx, dy, dz;

			BlockPos.PooledMutableBlockPos mutPos = BlockPos.PooledMutableBlockPos.retain();

			if (rawLight > light) {
				this.lightUpdateBlockList[bufferIndex++] = 133152;
			} else if (rawLight < light) {
				this.lightUpdateBlockList[bufferIndex++] = 133152 | light << 18;

				int index2 = 0;
				outer:
				while (true) {
					int exceptLight;
					do {
						do {
							do {
								if (index2 >= bufferIndex) {
									break outer;
								}

								int data = this.lightUpdateBlockList[index2++];
								x1 = (data & 63) - 32 + x;
								y1 = (data >> 6 & 63) - 32 + y;
								z1 = (data >> 12 & 63) - 32 + z;

								exceptLight = data >> 18 & 15;

								realLight = this.getLightFor(lightType, mutPos.setPos(x1, y1, z1));
							} while (realLight != exceptLight);

							this.setLightFor(lightType, mutPos, 0);
						} while (exceptLight <= 0);

						dx = MathHelper.abs(x1 - x);
						dy = MathHelper.abs(y1 - y);
						dz = MathHelper.abs(z1 - z);
					} while (dx + dy + dz >= updateRange);

					for (EnumFacing facing : EnumFacing.VALUES) {
						int xP = x1 + facing.getXOffset();
						int yP = y1 + facing.getYOffset();
						int zP = z1 + facing.getZOffset();
						IBlockState state = this.getBlockState(mutPos.setPos(xP, yP, zP));
						int opacity = Math.max(1, state.getBlock().getLightOpacity(state, this, mutPos));
						realLight = this.getLightFor(lightType, mutPos);
						if (realLight == exceptLight - opacity && bufferIndex < this.lightUpdateBlockList.length) {
							this.lightUpdateBlockList[bufferIndex++] = xP - x + 32 | yP - y + 32 << 6 | zP - z + 32 << 12 | exceptLight - opacity << 18;
						}
					}
				}
			}

			this.profiler.endSection();
			this.profiler.startSection("checkedPosition < toCheckCount");

			int i = 0;
			while (i < bufferIndex) {
				int data = this.lightUpdateBlockList[i++];
				x1 = (data & 63) - 32 + x;
				y1 = (data >> 6 & 63) - 32 + y;
				z1 = (data >> 12 & 63) - 32 + z;

				int light1 = this.getLightFor(lightType, mutPos.setPos(x1, y1, z1));
				realLight = this.getRawLight(mutPos, lightType);
				if (realLight != light1) {
					this.setLightFor(lightType, mutPos, realLight);
					if (realLight > light1) {
						dx = Math.abs(x1 - x);
						dy = Math.abs(y1 - y);
						dz = Math.abs(z1 - z);
						boolean hasMoreSpace = bufferIndex < this.lightUpdateBlockList.length - 6;
						if (dx + dy + dz < updateRange && hasMoreSpace) {

							int xT = x1 - x + 32;
							int yT = y1 - y + 32;
							int zT = z1 - z + 32;

							for (EnumFacing face : EnumFacing.VALUES) {
								if (this.getLightFor(lightType, mutPos.setPos(x1, y1, z1).move(face)) < realLight) {
									this.lightUpdateBlockList[bufferIndex++] = getData123(xT, yT, zT, face);
								}
							}
						}
					}
				}
			}

			mutPos.release();

			this.profiler.endSection();
			return true;
		}
	}

	@Copy
	private static int getData123(int xT, int yT, int zT, EnumFacing face) {
		int base = face.getAxisDirection().getOffset();

		switch (face.getAxis().ordinal()) {
			case 0:
				xT += base;
				break;
			case 1:
				yT += base;
				break;
			case 2:
				zT += base;
				break;
		}

		return xT + (yT << 6) + (zT << 12);
	}
}