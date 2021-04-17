package ilib.asm.nx;

import ilib.misc.MCHooks;
import ilib.util.Reflection;
import org.apache.commons.lang3.mutable.MutableDouble;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.collect.FilterList;
import roj.collect.SimpleList;
import roj.concurrent.OperationDone;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.EntitySelectors;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;

import net.minecraftforge.event.ForgeEventFactory;

import javax.annotation.Nullable;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
@Nixim("/")
abstract class NxGetCollision extends World {
	NxGetCollision() {
		super(null, null, null, null, false);
	}

	@Inject
	private boolean getCollisionBoxes(@Nullable Entity entityIn, AxisAlignedBB aabb, boolean canOutWorld, @Nullable List<AxisAlignedBB> outList) {
		if (outList == null) throw new NullPointerException("outList");

		int minX = MathHelper.floor(aabb.minX) - 1;
		int maxX = MathHelper.ceil(aabb.maxX) + 1;
		int minY = MathHelper.floor(aabb.minY) - 1;
		int maxY = MathHelper.ceil(aabb.maxY) + 1;
		int minZ = MathHelper.floor(aabb.minZ) - 1;
		int maxZ = MathHelper.ceil(aabb.maxZ) + 1;

		WorldBorder border = this.getWorldBorder();

		final boolean entityInBorder = entityIn != null && this.isInsideWorldBorder(entityIn);
		if (entityIn != null) {
			if (entityInBorder == entityIn.isOutsideBorder()) {
				entityIn.setOutsideBorder(!entityInBorder);
			}
		}

		IBlockState state = Blocks.STONE.getDefaultState();

		BlockPos.PooledMutableBlockPos mutPos = BlockPos.PooledMutableBlockPos.retain();

		if (canOutWorld && !ForgeEventFactory.gatherCollisionBoxes(this, entityIn, aabb, outList)) {
			return true;
		} else {
			if (canOutWorld) {
				if (minX < -30000000 || maxX >= 30000000 || minZ < -30000000 || maxZ >= 30000000) {
					return true;
				}
			}
			for (int x = minX; x < maxX; ++x) {
				for (int z = minZ; z < maxZ; ++z) {
					final boolean xCorner = x == minX || x == maxX - 1;
					final boolean zCorner = z == minZ || z == maxZ - 1;
					if (!(xCorner & zCorner) && this.isBlockLoaded(mutPos.setPos(x, 64, z))) {
						final boolean flag = !xCorner && !zCorner;
						for (int y = minY; y < maxY; ++y) {
							if (flag || y != maxY - 1) {
								mutPos.setPos(x, y, z);
								IBlockState state1;
								if (!canOutWorld && !border.contains(mutPos) && entityInBorder) {
									state1 = state;
								} else {
									state1 = this.getBlockState(mutPos);
								}

								state1.addCollisionBoxToList(this, mutPos, aabb, outList, entityIn, false);
								if (canOutWorld && !ForgeEventFactory.gatherCollisionBoxes(this, entityIn, aabb, outList)) {
									mutPos.release();
									return true;
								}
							}
						}
					}
				}
			}

			mutPos.release();
			return !outList.isEmpty();
		}
	}

	@Inject("/")
	public boolean collidesWithAnyBlock(AxisAlignedBB aabb) {
		int minX = MathHelper.floor(aabb.minX) - 1;
		int maxX = MathHelper.ceil(aabb.maxX) + 1;
		int minY = MathHelper.floor(aabb.minY) - 1;
		int maxY = MathHelper.ceil(aabb.maxY) + 1;
		int minZ = MathHelper.floor(aabb.minZ) - 1;
		int maxZ = MathHelper.ceil(aabb.maxZ) + 1;

		BlockPos.PooledMutableBlockPos mutPos = BlockPos.PooledMutableBlockPos.retain();
		SimpleList<AxisAlignedBB> outList = new SimpleList<>();

		if (!ForgeEventFactory.gatherCollisionBoxes(this, null, aabb, outList)) {
			return true;
		} else {
			if (minX < -30000000 || maxX >= 30000000 || minZ < -30000000 || maxZ >= 30000000) {
				return true;
			}
			for (int x = minX; x < maxX; ++x) {
				for (int z = minZ; z < maxZ; ++z) {
					final boolean xCorner = x == minX || x == maxX - 1;
					final boolean zCorner = z == minZ || z == maxZ - 1;
					if (!(xCorner & zCorner) && this.isBlockLoaded(mutPos.setPos(x, 64, z))) {
						final boolean flag = !xCorner && !zCorner;
						for (int y = minY; y < maxY; ++y) {
							if (flag || y != maxY - 1) {
								IBlockState state1 = this.getBlockState(mutPos.setPos(x, y, z));

								outList.clear();
								state1.addCollisionBoxToList(this, mutPos, aabb, outList, null, false);
								if (!ForgeEventFactory.gatherCollisionBoxes(this, null, aabb, outList)) {
									mutPos.release();
									return true;
								}
							}
						}
					}
				}
			}
			mutPos.release();
			return false;
		}
	}

	@Inject("/")
	public boolean checkNoEntityCollision(AxisAlignedBB aabb, @Nullable Entity entityIn) {
		int minX = MathHelper.floor((aabb.minX - MAX_ENTITY_RADIUS) / 16);
		int maxX = MathHelper.ceil((aabb.maxX + MAX_ENTITY_RADIUS) / 16);
		int minZ = MathHelper.floor((aabb.minZ - MAX_ENTITY_RADIUS) / 16);
		int maxZ = MathHelper.ceil((aabb.maxZ + MAX_ENTITY_RADIUS) / 16);

		FilterList<Entity> list = MCHooks.getEntityAliveFilter(entityIn);

		try {
			for (int x = minX; x < maxX; ++x) {
				for (int z = minZ; z < maxZ; ++z) {
					if (this.isChunkLoaded(x, z, true)) {
						this.getChunk(x, z).getEntitiesWithinAABBForEntity(entityIn, aabb, list, EntitySelectors.NOT_SPECTATING);
					}
				}
			}
		} catch (OperationDone e) {
			return false;
		}

		return true;
	}

	@Inject("/")
	public float getBlockDensity(Vec3d vec, AxisAlignedBB bb) {
		final double dx = bb.maxX - bb.minX;
		final double dy = bb.maxY - bb.minY;
		final double dz = bb.maxZ - bb.minZ;

		double dpx = 1 / (dx * 2 + 1);
		double dpy = 1 / (dy * 2 + 1);
		double dpz = 1 / (dz * 2 + 1);

		if (dpx >= 0 && dpy >= 0 && dpz >= 0) {
			int isBlock = 0;
			int all = 0;

			double kx = (1 - Math.floor(1 / dpx) * dpx) / 2;
			double kz = (1 - Math.floor(1 / dpz) * dpz) / 2;

			Vec3d vec1 = new Vec3d(0, 0, 0);

			for (double xP = 0; xP <= 1; xP = (xP + dpx)) {
				for (double yP = 0; yP <= 1; yP = (yP + dpy)) {
					for (double zP = 0; zP <= 1; zP = (zP + dpz)) {
						double cx = bb.minX + dx * xP;
						double cy = bb.minY + dy * yP;
						double cz = bb.minZ + dz * zP;

						Reflection.HELPER.setVecX(vec1, cx + kx);
						Reflection.HELPER.setVecY(vec1, cy);
						Reflection.HELPER.setVecZ(vec1, cz + kz);
						if (rayTraceBlocks(vec1, vec) == null) {
							++isBlock;
						}

						++all;
					}
				}
			}

			return (float) isBlock / (float) all;
		} else {
			return 0;
		}
	}

	@Nullable
	@Inject("/")
	public <T extends Entity> T findNearestEntityWithinAABB(Class<? extends T> entityType, AxisAlignedBB aabb, T closestTo) {
		int minX = MathHelper.floor((aabb.minX - MAX_ENTITY_RADIUS) / 16);
		int maxX = MathHelper.ceil((aabb.maxX + MAX_ENTITY_RADIUS) / 16);
		int minZ = MathHelper.floor((aabb.minZ - MAX_ENTITY_RADIUS) / 16);
		int maxZ = MathHelper.ceil((aabb.maxZ + MAX_ENTITY_RADIUS) / 16);

		MutableDouble mutableDouble = new MutableDouble(Double.MAX_VALUE);

		FilterList<T> list = new FilterList<>(((old, latest) -> {
			if (latest != closestTo) {
				double min = mutableDouble.doubleValue();
				double curr = closestTo.getDistanceSq(latest);
				if (curr < min) {
					mutableDouble.setValue(curr);
					return true;
				}
			}
			return false;
		}));

		for (int x = minX; x < maxX; ++x) {
			for (int z = minZ; z < maxZ; ++z) {
				if (this.isChunkLoaded(x, z, true)) {
					this.getChunk(x, z).getEntitiesOfTypeWithinAABB(entityType, aabb, list, EntitySelectors.NOT_SPECTATING);
				}
			}
		}

		return list.found;
	}

}