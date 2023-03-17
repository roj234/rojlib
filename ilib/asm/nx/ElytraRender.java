package ilib.asm.nx;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Inject.At;
import roj.asm.nixim.Nixim;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
@Nixim("net.minecraft.client.renderer.entity.RenderPlayer")
abstract class ElytraRender extends RenderLivingBase<AbstractClientPlayer> {
	public ElytraRender(RenderManager renderManagerIn, ModelBase modelBaseIn, float shadowSizeIn) {
		super(renderManagerIn, modelBaseIn, shadowSizeIn);
	}

	@Override
	@Inject(value = "func_77043_a", at = At.REPLACE)
	protected void applyRotations(AbstractClientPlayer player, float x, float y, float z) {
		if (player.isEntityAlive() && player.isPlayerSleeping()) {
			GlStateManager.rotate(player.getBedOrientationInDegrees(), 0.0F, 1.0F, 0.0F);
			GlStateManager.rotate(this.getDeathMaxRotation(player), 0.0F, 0.0F, 1.0F);
			GlStateManager.rotate(270.0F, 0.0F, 1.0F, 0.0F);
		} else if (player.isElytraFlying()) {
			super.applyRotations(player, x, y, z);
			float f = (float) player.getTicksElytraFlying() + z;
			float f1 = MathHelper.clamp(f * f / 100.0F, 0.0F, 1.0F);
			GlStateManager.rotate(f1 * (-90.0F - player.rotationPitch), 1.0F, 0.0F, 0.0F);
			Vec3d vec3d = player.getLook(z);
			double d0 = player.motionX * player.motionX + player.motionZ * player.motionZ;
			double d1 = vec3d.x * vec3d.x + vec3d.z * vec3d.z;
			if (d0 > 0.0D && d1 > 0.0D) {
				double d2 = (player.motionX * vec3d.x + player.motionZ * vec3d.z) / (Math.sqrt(d0) * Math.sqrt(d1));
				double d3 = player.motionX * vec3d.z - player.motionZ * vec3d.x;
				// patchClient
				GlStateManager.rotate((float) (Math.signum(d3) * Math.acos(Math.min(d2, 1.0D))) * 180.0F / 3.1415927F, 0.0F, 1.0F, 0.0F);
			}
		} else {
			super.applyRotations(player, x, y, z);
		}
	}
}
