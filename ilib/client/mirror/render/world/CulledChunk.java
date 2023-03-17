package ilib.client.mirror.render.world;

import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.world.ChunkCache;

import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.fml.client.FMLClientHandler;

/**
 * @author Roj233
 * @since 2022/4/20 4:10
 */
public interface CulledChunk {
	void setCullFunc(CulledVboChunk.CullFunc fn);

	void setOfWorldView(ChunkCache cc);

	ChunkCache getOfWorldView();

	static ChunkCache getWorldView(RenderChunk rc) {
		if (FMLClientHandler.instance().hasOptifine()) {
			CulledChunk cc = (CulledChunk) rc;
			if (cc.getOfWorldView() != null) return cc.getOfWorldView();
			ChunkCache cache = new ChunkCache(rc.world, rc.position.add(-1, -1, -1), rc.position.add(16, 16, 16), 1);
			MinecraftForgeClient.onRebuildChunk(rc.world, rc.position, cache);
			cc.setOfWorldView(cache);
			return cache;
		} else {
			return rc.worldView;
		}
	}
}
