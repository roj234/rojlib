package ilib.asm.nx.client.crd;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.util.math.BlockPos;

import net.minecraftforge.client.model.pipeline.VertexBufferConsumer;

/**
 * @author Roj233
 * @since 2022/4/23 0:00
 */
@Nixim("net.minecraftforge.client.model.pipeline.VertexBufferConsumer")
class NxBufCom extends VertexBufferConsumer {
	@Shadow("/")
	private BlockPos offset;

	@Inject("/")
	public void setOffset(BlockPos offset) {
		this.offset = offset;
	}
}
