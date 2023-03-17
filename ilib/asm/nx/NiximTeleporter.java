package ilib.asm.nx;

import ilib.util.PortalCache;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.block.state.pattern.BlockPattern;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Teleporter;
import net.minecraft.world.chunk.Chunk;

import java.util.Set;

/**
 * @author Roj234
 * @since 2020/9/20 1:54
 */
@Nixim("net.minecraft.world.Teleporter")
class NiximTeleporter extends Teleporter {
	NiximTeleporter() {super(null);}

	@Override
	@Inject("/")
	public boolean placeInExistingPortal(Entity e, float rotationYaw) {
		double minDist = Double.POSITIVE_INFINITY;
		boolean doCache = true;

		BlockPos target = BlockPos.ORIGIN;

		long entityChunk = ChunkPos.asLong(MathHelper.floor(e.posX), MathHelper.floor(e.posZ));

		Teleporter.PortalPosition vCache = destinationCoordinateCache.get(entityChunk);
		if (vCache != null) {
			minDist = 0.0D;
			target = vCache;
			vCache.lastUpdateTime = world.getTotalWorldTime();
			doCache = false;
		} else {
			BlockPos center = new BlockPos(e);

			BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain();

			int minX = center.getX() - 128, minZ = center.getZ() - 128, maxX = center.getX() + 128, maxZ = center.getZ() + 128;
			PortalCache cache = e.dimension == -1 ? PortalCache.NETHER_CACHE : PortalCache.OVERWORLD_CACHE;

			for (int cx = (center.getX() >> 4) - 8, cxe = cx + 16; cx <= cxe; ++cx) {
				for (int cz = (center.getZ() >> 4) - 8, cze = cx + 16; cz <= cze; ++cz) {
					Set<BlockPos> set = cache.computeIfAbsent(cx, cz);
					if (set.contains(null)) {
						set.remove(null);
						Chunk c = world.getChunk(cx, cz);
						for (int dx = 0; dx < 16; ++dx) {
							for (int dz = 0; dz < 16; ++dz) {
								for (int y = Math.min(world.getActualHeight() - 1, c.getTopFilledSegment() + 16); y >= 0; --y) {
									if (c.getBlockState(dx, y, dz).getBlock() == Blocks.PORTAL) {
										set.add(pos.setPos((cx << 4) + dx, y, (cz << 4) + dz).toImmutable());

										if (pos.getX() >= minX && pos.getX() <= maxX && pos.getZ() >= minZ && pos.getZ() <= maxZ) {
											double dist = pos.distanceSq(center);
											if (dist < minDist || (dist == minDist && pos.getY() < target.getY())) {
												minDist = dist;
												target = pos;
											}
										}
									}
								}
							}
						}
					} else {
						for (BlockPos pp : set) {
							if (pp.getX() >= minX && pp.getX() <= maxX && pp.getZ() >= minZ && pp.getZ() <= maxZ) {
								double dist = pp.distanceSq(center);
								if (dist < minDist || (dist == minDist && pp.getY() < target.getY())) {
									minDist = dist;
									target = pp;
								}
							}
						}
					}
				}
			}

			pos.release();
		}

		if (minDist < Double.MAX_VALUE) {
			if (doCache) {
				destinationCoordinateCache.put(entityChunk, new PortalPosition(target, world.getTotalWorldTime()));
			}

			double x = (double) target.getX() + 0.5D;
			double z = (double) target.getZ() + 0.5D;
			BlockPattern.PatternHelper helper = Blocks.PORTAL.createPatternHelper(world, target);

			final EnumFacing forwards = helper.getForwards();
			final EnumFacing.AxisDirection axis = forwards.rotateY().getAxisDirection();

			boolean isNegative = axis == EnumFacing.AxisDirection.NEGATIVE;

			double xz = forwards.getAxis() == EnumFacing.Axis.X ? (double) helper.getFrontTopLeft().getZ() : (double) helper.getFrontTopLeft().getX();
			double y = (double) (helper.getFrontTopLeft().getY() + 1) - e.getLastPortalVec().y * (double) helper.getHeight();

			if (isNegative) {
				++xz;
			}

			if (forwards.getAxis() == EnumFacing.Axis.X) {
				z = xz + (1.0D - e.getLastPortalVec().x) * (double) helper.getWidth() * (double) axis.getOffset();
			} else {
				x = xz + (1.0D - e.getLastPortalVec().x) * (double) helper.getWidth() * (double) axis.getOffset();
			}

			float f = 0.0F;
			float f1 = 0.0F;
			float f2 = 0.0F;
			float f3 = 0.0F;

			final EnumFacing opposite = forwards.getOpposite();
			final EnumFacing teleportDirection = e.getTeleportDirection();
			if (opposite == teleportDirection) {
				f = 1.0F;
				f1 = 1.0F;
			} else if (opposite == teleportDirection.getOpposite()) {
				f = -1.0F;
				f1 = -1.0F;
			} else if (opposite == teleportDirection.rotateY()) {
				f2 = 1.0F;
				f3 = -1.0F;
			} else {
				f2 = -1.0F;
				f3 = 1.0F;
			}

			double mx = e.motionX;
			double mz = e.motionZ;
			e.motionX = mx * (double) f + mz * (double) f3;
			e.motionZ = mx * (double) f2 + mz * (double) f1;
			e.rotationYaw = rotationYaw - (float) (teleportDirection.getOpposite().getHorizontalIndex() * 90) + (float) (forwards.getHorizontalIndex() * 90);

			if (e instanceof EntityPlayerMP) {
				((EntityPlayerMP) e).connection.setPlayerLocation(x, y, z, e.rotationYaw, e.rotationPitch);
			} else {
				e.setLocationAndAngles(x, y, z, e.rotationYaw, e.rotationPitch);
			}

			return true;
		} else {
			return false;
		}
	}

	@Override
	@Inject(value = "/", at = Inject.At.TAIL)
	public void removeStalePortalLocations(long worldTime) {
		PortalCache.removeStalePortalLocations(worldTime);
	}
}
