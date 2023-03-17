package ilib.asm.nx.client;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.network.play.server.SPacketUnloadChunk;

@Nixim("net.minecraft.client.network.NetHandlerPlayClient")
abstract class FarSight {
	@Inject
	public void processChunkUnload(SPacketUnloadChunk chunk) {}
}
