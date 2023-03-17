package ilib.asm.nx.client;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.util.BlockRenderLayer;

/**
 * @author solo6975
 * @since 2022/4/1 16:01
 */
@Nixim("net.minecraft.block.BlockLiquid")
class NxClearLava {
	@Inject("func_180664_k")
	public BlockRenderLayer getRenderLayer() {
		return BlockRenderLayer.TRANSLUCENT;
	}
}
