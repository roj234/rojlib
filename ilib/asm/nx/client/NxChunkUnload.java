package ilib.asm.nx.client;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;

/**
 * @author solo6975
 * @since 2022/5/2 22:59
 */
@Nixim("net.minecraft.client.multiplayer.WorldClient")
abstract class NxChunkUnload extends WorldClient {
	@Shadow("field_73033_b")
	private ChunkProviderClient clientChunkProvider;

	NxChunkUnload() {
		super(null, null, 0, null, null);
	}

	@Inject("/")
	public void doPreChunk(int chunkX, int chunkZ, boolean loadChunk) {
		if (loadChunk) {
			clientChunkProvider.loadChunk(chunkX, chunkZ);
		} else {
			clientChunkProvider.unloadChunk(chunkX, chunkZ);
		}
	}
}
