package ilib.asm.nx.client;

import ilib.asm.util.CameraAccess;
import ilib.asm.util.MCHooksClient;
import ilib.util.Reflection;
import roj.RequireTest;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * @author solo6975
 * @since 2022/5/3 0:29
 */
@Nixim("net.minecraft.client.particle.ParticleManager")
@RequireTest
class NxParticleCull extends ParticleManager {
	public NxParticleCull() {
		super(null, null);
	}

	@Copy(unique = true, staticInitializer = "initab", targetIsFinal = true)
	private static Vec3d a;
	@Copy(unique = true)
	private static Vec3d b;

	private static void initab() {
		a = new Vec3d(0, 0, 0);
		b = new Vec3d(0, 0, 0);
	}

	@Inject(value = "/", at = Inject.At.INVOKE, param = {"func_180434_a", "redirect0"}, flags = Inject.FLAG_OPTIONAL)
	public void renderParticles(Entity entityIn, float partialTicks) {}

	@Inject(value = "/", at = Inject.At.INVOKE, param = {"func_180434_a", "redirect0"}, flags = Inject.FLAG_OPTIONAL)
	public void renderLitParticles(Entity entityIn, float partialTick) {}

	@Copy(unique = true)
	public static void redirect0(Particle particle, BufferBuilder builder, Entity entity, float f1, float f2, float f3, float f4, float f5, float f6) {
		if (noCulledBy(particle, entity)) particle.renderParticle(builder, entity, f1, f2, f3, f4, f5, f6);
	}

	@Copy(unique = true)
	public static boolean noCulledBy(Particle particle, Entity entity) {
		if (!particle.getClass().getName().startsWith("net.minecraft")) return true;

		ICamera camera = ((CameraAccess) Minecraft.getMinecraft().renderGlobal).getCamera();

		if (camera == null) return true;

		if (camera.isBoundingBoxInFrustum(particle.getBoundingBox())) {
			Reflection.setVec(a, entity.posX, entity.posY + entity.getEyeHeight(), entity.posZ);
			Reflection.setVec(b, particle.posX, particle.posY, particle.posZ);
			return entity.getDistanceSq(particle.posX, particle.posY, particle.posZ) < 50 || MCHooksClient.rayTraceOpaque(entity.world, a, b);
		}

		return false;
	}
}
