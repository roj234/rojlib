package ilib.misc;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class EntityTransform {
	private final Entity entity;
	private final double x;
	private final double y;
	private final double z;
	private final float yaw;
	private final float pitch;

	public EntityTransform(Entity entity, double x, double y, double z, float yaw, float pitch) {
		this.entity = entity;
		this.x = x;
		this.y = y;
		this.z = z;
		this.yaw = yaw;
		this.pitch = pitch;
	}

	public void apply() {
		if (entity.world.isRemote) {
			applyClient();
		} else {
			applyMain();
		}
	}

	//This fixes the hand bouncing around when you look around
	@SideOnly(Side.CLIENT)
	private void applyClient() {
		float prevYaw = 0.0F;
		float yaw = 0.0F;
		float prevPitch = 0.0F;
		float pitch = 0.0F;

		if (entity instanceof EntityPlayerSP) {
			EntityPlayerSP player = (EntityPlayerSP) entity;
			prevYaw = player.prevRotationYaw - player.prevRenderArmYaw;
			yaw = player.rotationYaw - player.renderArmYaw;
			prevPitch = player.prevRotationPitch - player.prevRenderArmPitch;
			pitch = player.rotationPitch - player.renderArmPitch;
		}

		applyMain();

		if (entity instanceof EntityPlayerSP) {
			EntityPlayerSP player = (EntityPlayerSP) entity;
			player.prevRenderArmYaw = player.prevRotationYaw;
			player.renderArmYaw = player.rotationYaw;
			player.prevRenderArmPitch = player.prevRotationPitch;
			player.renderArmPitch = player.rotationPitch;
			player.prevRenderArmYaw -= prevYaw;
			player.renderArmYaw -= yaw;
			player.prevRenderArmPitch -= prevPitch;
			player.renderArmPitch -= pitch;
		}
	}

	private void applyMain() {
		Entity entity = this.entity;
		entity.posX += x;
		entity.posY += y;
		entity.posZ += z;
		entity.prevPosX += x;
		entity.prevPosY += y;
		entity.prevPosZ += z;
		entity.lastTickPosX += x;
		entity.lastTickPosY += y;
		entity.lastTickPosZ += z;

		entity.rotationPitch += pitch;
		entity.rotationYaw += yaw;
		entity.prevRotationPitch += pitch;
		entity.prevRotationYaw += yaw;

		if (entity instanceof EntityLivingBase) {
			EntityLivingBase living = (EntityLivingBase) entity;
			living.rotationYawHead += yaw;
			living.prevRotationYawHead += yaw;
		}
	}

	public void revert() {
		if (entity.world.isRemote) {
			revertClient();
		} else {
			revertMain();
		}
	}

	//This fixes the hand bouncing around when you look around
	@SideOnly(Side.CLIENT)
	private void revertClient() {
		float prevYaw = 0.0F;
		float yaw = 0.0F;
		float prevPitch = 0.0F;
		float pitch = 0.0F;

		if (entity instanceof EntityPlayerSP) {
			EntityPlayerSP player = (EntityPlayerSP) entity;
			prevYaw = player.prevRotationYaw - player.prevRenderArmYaw;
			yaw = player.rotationYaw - player.renderArmYaw;
			prevPitch = player.prevRotationPitch - player.prevRenderArmPitch;
			pitch = player.rotationPitch - player.renderArmPitch;
		}

		revertMain();

		if (entity instanceof EntityPlayerSP) {
			EntityPlayerSP player = (EntityPlayerSP) entity;
			player.prevRenderArmYaw = player.prevRotationYaw;
			player.renderArmYaw = player.rotationYaw;
			player.prevRenderArmPitch = player.prevRotationPitch;
			player.renderArmPitch = player.rotationPitch;
			player.prevRenderArmYaw -= prevYaw;
			player.renderArmYaw -= yaw;
			player.prevRenderArmPitch -= prevPitch;
			player.renderArmPitch -= pitch;
		}
	}

	private void revertMain() {
		Entity entity = this.entity;
		entity.posX -= x;
		entity.posY -= y;
		entity.posZ -= z;
		entity.prevPosX -= x;
		entity.prevPosY -= y;
		entity.prevPosZ -= z;
		entity.lastTickPosX -= x;
		entity.lastTickPosY -= y;
		entity.lastTickPosZ -= z;

		entity.rotationPitch -= pitch;
		entity.rotationYaw -= yaw;
		entity.prevRotationPitch -= pitch;
		entity.prevRotationYaw -= yaw;

		if (entity instanceof EntityLivingBase) {
			EntityLivingBase living = (EntityLivingBase) entity;
			living.rotationYawHead -= yaw;
			living.prevRotationYawHead -= yaw;
		}
	}
}