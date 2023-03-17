package ilib.client.misc;

import ilib.ClientProxy;
import ilib.client.RenderUtils;
import org.lwjgl.opengl.GL11;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class ParticleEntityBlood extends Particle {
	public static final ResourceLocation PARTICLE_TEXTURES = new ResourceLocation("textures/particle/particles.png");

	private int dropTime;

	public ParticleEntityBlood(World worldIn, double posX, double posY, double posZ, int color, int maxAge) {
		super(worldIn, posX, posY, posZ, 0, 0, 0);
		this.particleMaxAge = maxAge + rand.nextInt((maxAge / 4) + 1);
		this.particleGravity = 0.36F;
		this.particleScale = (rand.nextFloat() * 0.5F + 0.5F);
		this.dropTime = 40;

		float spread = 0.15F;
		motionX = (rand.nextFloat() - rand.nextFloat()) * spread;
		motionY = rand.nextFloat() * 0.4F + 0.05F;
		motionZ = (rand.nextFloat() - rand.nextFloat()) * spread;

		particleAlpha = ((color >> 24) & 0xFF) / 255F;
		particleRed = ((color >> 16) & 0xFF) / 255F;
		particleGreen = ((color >> 8) & 0xFF) / 255F;
		particleBlue = (color & 0xFF) / 255F;
	}

	@Override
	public void onUpdate() {
		prevPosX = posX;
		prevPosY = posY;
		prevPosZ = posZ;

		motionY -= particleGravity;

		move(motionX, motionY, motionZ);

		motionX *= 0.98;
		motionY *= 0.98;
		motionZ *= 0.98;

		if (dropTime-- > 0) {
			motionX *= 0.2;
			motionY *= 0.05;
			motionZ *= 0.2;
		}

		if (particleMaxAge-- <= 0) {
			setExpired();
		}

		if (onGround) {
			motionX *= 0.7;
			motionZ *= 0.7;
		}

		BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain(posX, posY, posZ);
		IBlockState state = world.getBlockState(pos);
		if (state.getMaterial().isLiquid()) {
			setExpired();
		}
		pos.release();
	}

	public int getBrightnessForRender(float pt) {
		BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain(posX, posY, posZ);
		try {
			return world.isBlockLoaded(pos) ? world.getCombinedLight(pos, 0) : 0;
		} finally {
			pos.release();
		}
	}

	@Override
	public void renderParticle(BufferBuilder bb, Entity entity, float partialTicks, float rX, float rZ, float rYZ, float rXY, float rXZ) {
		GlStateManager.disableBlend();

		RenderUtils.bindTexture(PARTICLE_TEXTURES);
		RenderUtils.colorWhite();

		float r = this.particleRed;
		float g = this.particleGreen;
		float b = this.particleBlue;
		float a = this.particleAlpha;

		float u2 = (float) this.particleTextureIndexX / 16.0F;
		float u1 = u2 + 0.0624375F;
		float v2 = (float) this.particleTextureIndexY / 16.0F;
		float v1 = v2 + 0.0624375F;
		float pScale = 1F * this.particleScale;
		float pyScale = 0.1F * this.particleScale;

		if (this.particleTexture != null) {
			u2 = this.particleTexture.getMinU();
			u1 = this.particleTexture.getMaxU();
			v2 = this.particleTexture.getMinV();
			v1 = this.particleTexture.getMaxV();
		}

		Entity rve = ClientProxy.mc.getRenderViewEntity();

		double ipx = rve.lastTickPosX + (rve.posX - rve.lastTickPosX) * (double) partialTicks;
		double ipy = rve.lastTickPosY + (rve.posY - rve.lastTickPosY) * (double) partialTicks;
		double ipz = rve.lastTickPosZ + (rve.posZ - rve.lastTickPosZ) * (double) partialTicks;

		float x = (float) (prevPosX + (posX - prevPosX) * (double) partialTicks - ipx);
		float y = (float) (prevPosY + (posY - prevPosY) * (double) partialTicks - ipy + 0.02F);
		float z = (float) (prevPosZ + (posZ - prevPosZ) * (double) partialTicks - ipz);

		int brightness = this.getBrightnessForRender(partialTicks);
		int SL = brightness >> 16 & 65535;
		int BL = brightness & 65535;

		Vec3d[] vec = new Vec3d[] {new Vec3d(-rX * pScale - rXY * pScale, -rZ * pyScale, -rYZ * pScale - rXZ * pScale),
								   new Vec3d(-rX * pScale + rXY * pScale, rZ * pyScale, -rYZ * pScale + rXZ * pScale), new Vec3d(rX * pScale + rXY * pScale, rZ * pyScale, rYZ * pScale + rXZ * pScale),
								   new Vec3d(rX * pScale - rXY * pScale, -rZ * pyScale, rYZ * pScale - rXZ * pScale)};

		if (particleAngle != 0.0F) {
			float angle = particleAngle + (particleAngle - prevParticleAngle) * partialTicks;

			float scale = MathHelper.cos(angle * 0.5F);
			float viewX = MathHelper.sin(angle * 0.5F) * (float) cameraViewDir.x;
			float viewY = MathHelper.sin(angle * 0.5F) * (float) cameraViewDir.y;
			float viewZ = MathHelper.sin(angle * 0.5F) * (float) cameraViewDir.z;
			Vec3d view = new Vec3d(viewX, viewY, viewZ);

			for (int v = 0; v < 4; ++v) {
				vec[v] = view.scale(2.0D * vec[v].dotProduct(view)).add(vec[v].scale((double) (scale * scale) - view.dotProduct(view))).add(view.crossProduct(vec[v]).scale(2.0F * scale));
			}
		}

		bb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_LMAP_COLOR);

		bb.pos((double) x + vec[0].x, (double) y + vec[0].y, (double) z + vec[0].z).tex(u1, v1).color(r, g, b, a).lightmap(SL, BL).endVertex();
		bb.pos((double) x + vec[1].x, (double) y + vec[1].y, (double) z + vec[1].z).tex(u1, v2).color(r, g, b, a).lightmap(SL, BL).endVertex();
		bb.pos((double) x + vec[2].x, (double) y + vec[2].y, (double) z + vec[2].z).tex(u2, v2).color(r, g, b, a).lightmap(SL, BL).endVertex();
		bb.pos((double) x + vec[3].x, (double) y + vec[3].y, (double) z + vec[3].z).tex(u2, v1).color(r, g, b, a).lightmap(SL, BL).endVertex();

		RenderUtils.TESSELLATOR.draw();
	}

	@Override
	public int getFXLayer() {
		return 3;
	}
}
