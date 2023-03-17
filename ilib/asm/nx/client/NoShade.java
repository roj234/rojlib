package ilib.asm.nx.client;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

/**
 * @author solo6975
 * @since 2022/6/9 1:16
 */
@Nixim("net.minecraft.client.renderer.block.model.BakedQuad")
class NoShade {
	@Inject("/")
	public boolean shouldApplyDiffuseLighting() {
		return false;
	}
}
