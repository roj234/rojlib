package ilib.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Teleporter;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class TeleportHelper {
	public static void teleport(EntityPlayer player, int dimension, BlockPos dest, @Nullable EnumFacing direction) {
		teleport(player, dimension, dest.getX() + 0.5, dest.getY() + 1.5, dest.getZ() + 0.5, direction);
	}

	public static void teleport(EntityPlayer player, int dimensionId, double destX, double destY, double destZ, EnumFacing direction) {

		float rotationYaw = player.rotationYaw;
		float rotationPitch = player.rotationPitch;

		if (dimensionId != DimensionHelper.idFor(player.getEntityWorld())) {
			MinecraftServer server = player.world.getMinecraftServer();
			if (server != null) {
				teleport((EntityPlayerMP) player, dimensionId, destX, destY, destZ);
			}
		} else {
			if (player.isBeingRidden()) {
				player.removePassengers();
			}

			if (player.isRiding()) {
				player.dismountRidingEntity();
			}
		}

		if (direction != null) {
			fixOrientation(player, destX, destY, destZ, direction);
		} else {
			player.rotationYaw = rotationYaw;
			player.rotationPitch = rotationPitch;
		}

		player.setPositionAndUpdate(destX, destY, destZ);
	}

	private static void teleport(EntityPlayerMP player, int dimension, double x, double y, double z) {
		int oldDimension = player.getEntityWorld().provider.getDimension();
		MinecraftServer server = player.getEntityWorld().getMinecraftServer();
		WorldServer to = server.getWorld(dimension);
		player.addExperienceLevel(0);

		server.getPlayerList().transferPlayerToDimension(player, dimension, new MyTper(to, x, y, z));
		player.setPositionAndUpdate(x, y, z);
		if (oldDimension == 1) {
			player.setPositionAndUpdate(x, y, z);
			to.spawnEntity(player);
			to.updateEntityWithOptionalForce(player, false);
		}
	}

	public static Entity teleport(Entity entity, World dest, double newX, double newY, double newZ, @Nullable EnumFacing facing) {
		World world = entity.getEntityWorld();
		if ((entity instanceof EntityPlayer)) {
			teleport((EntityPlayer) entity, dest.provider.getDimension(), newX, newY, newZ, facing);
			return entity;
		}
		float rotationYaw = entity.rotationYaw;
		float rotationPitch = entity.rotationPitch;
		if (world.provider.getDimension() != dest.provider.getDimension()) {

			NBTTagCompound tag = new NBTTagCompound();
			entity.writeToNBT(tag);
			tag.removeTag("Dimension");

			world.removeEntity(entity);
			entity.isDead = false;
			world.updateEntityWithOptionalForce(entity, false);

			Entity newEnt = EntityList.newEntity(entity.getClass(), dest);
			newEnt.readFromNBT(tag);
			if (facing != null) {
				fixOrientation(newEnt, newX, newY, newZ, facing);
			} else {
				newEnt.rotationYaw = rotationYaw;
				newEnt.rotationPitch = rotationPitch;
			}
			newEnt.setLocationAndAngles(newX, newY, newZ, newEnt.rotationYaw, newEnt.rotationPitch);
			boolean flag = newEnt.forceSpawn;
			newEnt.forceSpawn = true;
			dest.spawnEntity(newEnt);
			newEnt.forceSpawn = flag;
			dest.updateEntityWithOptionalForce(newEnt, false);

			entity.isDead = true;

			((WorldServer) world).resetUpdateEntityTick();
			((WorldServer) dest).resetUpdateEntityTick();
			return newEnt;
		}
		if (facing != null) {
			fixOrientation(entity, newX, newY, newZ, facing);
		} else {
			entity.rotationYaw = rotationYaw;
			entity.rotationPitch = rotationPitch;
		}
		entity.setLocationAndAngles(newX, newY, newZ, entity.rotationYaw, entity.rotationPitch);
		dest.updateEntityWithOptionalForce(entity, false);
		return entity;
	}

	private static void facePosition(Entity entity, double newX, double newY, double newZ, BlockPos dest) {
		double d0 = dest.getX() - newX;
		double d1 = dest.getY() - (newY + entity.getEyeHeight());
		double d2 = dest.getZ() - newZ;

		double d3 = MathHelper.sqrt(d0 * d0 + d2 * d2);
		float f = (float) (MathHelper.atan2(d2, d0) * (180D / Math.PI)) - 90.0F;
		float f1 = (float) (-(MathHelper.atan2(d1, d3) * (180D / Math.PI)));
		entity.rotationPitch = updateRotation(entity.rotationPitch, f1);
		entity.rotationYaw = updateRotation(entity.rotationYaw, f);
	}

	private static float updateRotation(float angle, float targetAngle) {
		float f = MathHelper.wrapDegrees(targetAngle - angle);
		return angle + f;
	}

	private static void fixOrientation(Entity entity, double newX, double newY, double newZ, EnumFacing facing) {
		if (facing != EnumFacing.DOWN && facing != EnumFacing.UP) {
			facePosition(entity, newX, newY, newZ, new BlockPos(newX, newY, newZ).offset(facing, 4));
		}
	}

	public static final class MyTper extends Teleporter {
		private final double x, y, z;

		public MyTper(WorldServer world, double x, double y, double z) {
			super(world);
			this.x = x;
			this.y = y;
			this.z = z;
		}

		@Override
		public void placeInPortal(Entity entity, float yaw) {
			world.getChunk((int) this.x << 4, (int) this.z << 4);   //dummy load to maybe gen chunk

			//this.world.updateEntityWithOptionalForce(entity, false);
			entity.setPosition(x, y, z);
			//entity.setPositionAndRotation(this.x, this.y, this.z, yaw, entity.rotationPitch);
			entity.motionX = 0.0f;
			entity.motionY = 0.0f;
			entity.motionZ = 0.0f;
			entity.velocityChanged = true;
			entity.rotationYaw = yaw;
		}

	}
}