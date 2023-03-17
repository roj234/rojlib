package ilib.client.mirror;

import ilib.client.mirror.render.WorldRenderer;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.NiximSystem;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerList;

/**
 * @author Roj233
 * @since 2022/4/26 2:08
 */
@Nixim("net.minecraft.server.management.PlayerList")
class NxLockVD extends PlayerList {
	public NxLockVD(MinecraftServer server) {
		super(server);
	}

	@Inject(value = "/", at = Inject.At.HEAD)
	public void setViewDistance(int distance) {
		if (WorldRenderer.renderLevel > 0) return;
		NiximSystem.SpecMethods.$$$VALUE_V();
	}
}
