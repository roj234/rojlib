package ilib.asm.nx;

import ilib.misc.MutAABB;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockWall;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.ReportedException;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.Arrays;
import java.util.List;

/**
 * @author Roj233
 * @since 2022/5/6 17:17
 */
@Nixim("net.minecraft.entity.Entity")
abstract class FastMove extends Entity {
	@Shadow
	private long pistonDeltasGameTime;
	@Shadow
	private double[] pistonDeltas;
	@Shadow
	private int nextStepDistance;
	@Shadow
	private float nextFlap;
	@Shadow
	private int fire;

	public FastMove() {
		super(null);
	}

	@Copy
	private boolean noCollided(AxisAlignedBB box) {
		return world.checkNoEntityCollision(box, this) && !world.checkBlockCollision(box);
	}

	@Inject("/")
	public void move(MoverType type, double x, double y, double z) {
		if (x * x + y * y + z * z < 1 / 16384d) return;

		// region no collision
		if (noClip) {
			setEntityBoundingBox(getEntityBoundingBox().offset(x, y, z));
			resetPositionToBB();
			return;
		}
		// endregion
		// region piston move
		if (type == MoverType.PISTON) {
			long t = world.getTotalWorldTime();
			if (t != pistonDeltasGameTime) {
				Arrays.fill(pistonDeltas, 0);
				pistonDeltasGameTime = t;
			}

			double T;
			if (x != 0) {
				T = MathHelper.clamp(x + pistonDeltas[0], -0.51D, 0.51D);
				x = T - pistonDeltas[0];
				pistonDeltas[0] = T;
				if (Math.abs(x) <= 1e-5) return;
			} else if (y != 0) {
				T = MathHelper.clamp(y + pistonDeltas[1], -0.51D, 0.51D);
				y = T - pistonDeltas[1];
				pistonDeltas[1] = T;
				if (Math.abs(y) <= 1e-5) return;
			} else if (z != 0) {
				T = MathHelper.clamp(z + pistonDeltas[2], -0.51D, 0.51D);
				z = T - pistonDeltas[2];
				pistonDeltas[2] = T;
				if (Math.abs(z) <= 1e-5) return;
			} else {
				return;
			}
		}
		// endregion
		// region spider web
		if (isInWeb) {
			isInWeb = false;
			x *= 0.25;
			y *= 0.05;
			z *= 0.25;
			motionX = 0;
			motionY = 0;
			motionZ = 0;
		}
		// endregion

		world.profiler.startSection("move");
		double prevX = posX;
		double prevY = posY;
		double prevZ = posZ;

		double x1 = x;
		double y1 = y;
		double z1 = z;
		AxisAlignedBB ebb = getEntityBoundingBox();
		MutAABB mbb = EntityAABBFx.getBox();

		// region sneaking check
		// noinspection all
		if ((type == MoverType.SELF || type == MoverType.PLAYER) && onGround && isSneaking() && (Object) this instanceof EntityPlayer) {
			while (x != 0 && noCollided(mbb.offset0(ebb, x, -stepHeight, 0.0D))) {
				if (x < 0.05D && x >= -0.05D) {
					x = 0;
				} else if (x > 0.0D) {
					x -= 0.05D;
				} else {
					x += 0.05D;
				}
				x1 = x;
			}

			while (z != 0.0D && noCollided(ebb.offset(0.0D, -stepHeight, z))) {
				if (z < 0.05D && z >= -0.05D) {
					z = 0;
				} else if (z > 0) {
					z -= 0.05D;
				} else {
					z += 0.05D;
				}
				z1 = z;
			}

			while (x != 0 && z != 0 && noCollided(ebb.offset(x, -stepHeight, z))) {
				if (x < 0.05D && x >= -0.05D) {
					x = 0;
				} else if (x > 0.0D) {
					x -= 0.05D;
				} else {
					x += 0.05D;
				}

				x1 = x;
				if (z < 0.05D && z >= -0.05D) {
					z = 0.0D;
				} else if (z > 0.0D) {
					z -= 0.05D;
				} else {
					z += 0.05D;
				}
				z1 = z;
			}
		}
		// endregion

		List<AxisAlignedBB> list1 = world.getCollisionBoxes(this, getEntityBoundingBox().expand(x, y, z));
		AxisAlignedBB ebb1 = getEntityBoundingBox();
		int k5;
		int j6;
		if (y != 0.0D) {
			k5 = 0;

			for (j6 = list1.size(); k5 < j6; ++k5) {
				y = list1.get(k5).calculateYOffset(getEntityBoundingBox(), y);
			}

			setEntityBoundingBox(getEntityBoundingBox().offset(0.0D, y, 0.0D));
		}

		if (x != 0.0D) {
			k5 = 0;

			for (j6 = list1.size(); k5 < j6; ++k5) {
				x = list1.get(k5).calculateXOffset(getEntityBoundingBox(), x);
			}

			if (x != 0.0D) {
				setEntityBoundingBox(getEntityBoundingBox().offset(x, 0.0D, 0.0D));
			}
		}

		if (z != 0.0D) {
			k5 = 0;

			for (j6 = list1.size(); k5 < j6; ++k5) {
				z = list1.get(k5).calculateZOffset(getEntityBoundingBox(), z);
			}

			if (z != 0.0D) {
				setEntityBoundingBox(getEntityBoundingBox().offset(0.0D, 0.0D, z));
			}
		}

		boolean flag = onGround || y != y && y < 0.0D;
		double d8;
		if (stepHeight > 0.0F && flag && (x1 != x || z1 != z)) {
			double d14 = x;
			double d6 = y;
			double d7 = z;
			AxisAlignedBB axisalignedbb1 = getEntityBoundingBox();
			setEntityBoundingBox(ebb);
			y = stepHeight;
			List<AxisAlignedBB> list = world.getCollisionBoxes(this, getEntityBoundingBox().expand(x1, y, z1));
			AxisAlignedBB axisalignedbb2 = getEntityBoundingBox();
			AxisAlignedBB axisalignedbb3 = axisalignedbb2.expand(x1, 0.0D, z1);
			d8 = y;
			int j1 = 0;

			for (int k1 = list.size(); j1 < k1; ++j1) {
				d8 = list.get(j1).calculateYOffset(axisalignedbb3, d8);
			}

			axisalignedbb2 = axisalignedbb2.offset(0.0D, d8, 0.0D);
			double d18 = x1;
			int l1 = 0;

			for (int i2 = list.size(); l1 < i2; ++l1) {
				d18 = list.get(l1).calculateXOffset(axisalignedbb2, d18);
			}

			axisalignedbb2 = axisalignedbb2.offset(d18, 0.0D, 0.0D);
			double d19 = z1;
			int j2 = 0;

			for (int k2 = list.size(); j2 < k2; ++j2) {
				d19 = list.get(j2).calculateZOffset(axisalignedbb2, d19);
			}

			axisalignedbb2 = axisalignedbb2.offset(0.0D, 0.0D, d19);
			AxisAlignedBB axisalignedbb4 = getEntityBoundingBox();
			double d20 = y;
			int l2 = 0;

			for (int i3 = list.size(); l2 < i3; ++l2) {
				d20 = list.get(l2).calculateYOffset(axisalignedbb4, d20);
			}

			axisalignedbb4 = axisalignedbb4.offset(0.0D, d20, 0.0D);
			double d21 = x1;
			int j3 = 0;

			for (int k3 = list.size(); j3 < k3; ++j3) {
				d21 = list.get(j3).calculateXOffset(axisalignedbb4, d21);
			}

			axisalignedbb4 = axisalignedbb4.offset(d21, 0.0D, 0.0D);
			double d22 = z1;
			int l3 = 0;

			for (int i4 = list.size(); l3 < i4; ++l3) {
				d22 = list.get(l3).calculateZOffset(axisalignedbb4, d22);
			}

			axisalignedbb4 = axisalignedbb4.offset(0.0D, 0.0D, d22);
			double d23 = d18 * d18 + d19 * d19;
			double d9 = d21 * d21 + d22 * d22;
			if (d23 > d9) {
				x = d18;
				z = d19;
				y = -d8;
				setEntityBoundingBox(axisalignedbb2);
			} else {
				x = d21;
				z = d22;
				y = -d20;
				setEntityBoundingBox(axisalignedbb4);
			}

			int j4 = 0;

			for (int k4 = list.size(); j4 < k4; ++j4) {
				y = list.get(j4).calculateYOffset(getEntityBoundingBox(), y);
			}

			setEntityBoundingBox(getEntityBoundingBox().offset(0.0D, y, 0.0D));
			if (d14 * d14 + d7 * d7 >= x * x + z * z) {
				x = d14;
				y = d6;
				z = d7;
				setEntityBoundingBox(axisalignedbb1);
			}
		}

		world.profiler.endSection();
		world.profiler.startSection("rest");
		resetPositionToBB();
		collidedHorizontally = x1 != x || z1 != z;
		collidedVertically = y != y;
		onGround = collidedVertically && y1 < 0.0D;
		collided = collidedHorizontally || collidedVertically;
		j6 = MathHelper.floor(posX);
		int i1 = MathHelper.floor(posY - 0.20000000298023224D);
		int k6 = MathHelper.floor(posZ);
		BlockPos blockpos = new BlockPos(j6, i1, k6);
		IBlockState iblockstate = world.getBlockState(blockpos);
		if (iblockstate.getMaterial() == Material.AIR) {
			BlockPos blockpos1 = blockpos.down();
			IBlockState iblockstate1 = world.getBlockState(blockpos1);
			Block block1 = iblockstate1.getBlock();
			if (block1 instanceof BlockFence || block1 instanceof BlockWall || block1 instanceof BlockFenceGate) {
				iblockstate = iblockstate1;
				blockpos = blockpos1;
			}
		}

		updateFallState(y, onGround, iblockstate, blockpos);
		if (x1 != x) {
			motionX = 0.0D;
		}

		if (z1 != z) {
			motionZ = 0.0D;
		}

		Block block = iblockstate.getBlock();
		if (y1 != y) {
			block.onLanded(world, this);
		}

		if (canTriggerWalking() && (!onGround || !isSneaking() || !((Object) this instanceof EntityPlayer)) && !isRiding()) {
			double d15 = posX - prevX;
			double d16 = posY - prevY;
			d8 = posZ - prevZ;
			if (block != Blocks.LADDER) {
				d16 = 0.0D;
			}

			if (block != null && onGround) {
				block.onEntityWalk(world, blockpos, this);
			}

			distanceWalkedModified = (float) ((double) distanceWalkedModified + (double) MathHelper.sqrt(d15 * d15 + d8 * d8) * 0.6D);
			distanceWalkedOnStepModified = (float) ((double) distanceWalkedOnStepModified + (double) MathHelper.sqrt(d15 * d15 + d16 * d16 + d8 * d8) * 0.6D);
			if (distanceWalkedOnStepModified > (float) nextStepDistance && iblockstate.getMaterial() != Material.AIR) {
				nextStepDistance = (int) distanceWalkedOnStepModified + 1;
				if (!isInWater()) {
					playStepSound(blockpos, block);
				} else {
					Entity entity = isBeingRidden() && getControllingPassenger() != null ? getControllingPassenger() : this;
					float f = entity == this ? 0.35F : 0.4F;
					float f1 = MathHelper.sqrt(entity.motionX * entity.motionX * 0.20000000298023224D + entity.motionY * entity.motionY + entity.motionZ * entity.motionZ * 0.20000000298023224D) * f;
					if (f1 > 1.0F) {
						f1 = 1.0F;
					}

					playSound(getSwimSound(), f1, 1.0F + (rand.nextFloat() - rand.nextFloat()) * 0.4F);
				}
			} else if (distanceWalkedOnStepModified > nextFlap && makeFlySound() && iblockstate.getMaterial() == Material.AIR) {
				nextFlap = playFlySound(distanceWalkedOnStepModified);
			}
		}

		try {
			doBlockCollisions();
		} catch (Throwable var58) {
			CrashReport crashreport = CrashReport.makeCrashReport(var58, "Checking entity block collision");
			CrashReportCategory crashreportcategory = crashreport.makeCategory("Entity being checked for collision");
			addEntityCrashInfo(crashreportcategory);
			throw new ReportedException(crashreport);
		}

		boolean flag1 = isWet();
		if (world.isFlammableWithin(getEntityBoundingBox().shrink(0.001D))) {
			dealFireDamage(1);
			if (!flag1) {
				++fire;
				if (fire == 0) {
					setFire(8);
				}
			}
		} else if (fire <= 0) {
			fire = -getFireImmuneTicks();
		}

		if (flag1 && isBurning()) {
			playSound(SoundEvents.ENTITY_GENERIC_EXTINGUISH_FIRE, 0.7F, 1.6F + (rand.nextFloat() - rand.nextFloat()) * 0.4F);
			fire = -getFireImmuneTicks();
		}

		world.profiler.endSection();
	}
}
