package ilib.client.mirror.render.world;

import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.ListedRenderChunk;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;

/**
 * @author Roj233
 * @since 2022/4/20 1:13
 */
public class CulledListChunk extends ListedRenderChunk implements CulledChunk {
	CulledVboChunk.CullFunc cullFunc = CulledVboChunk.noCull;
	boolean wasCulled;

	private final int displayLists = GLAllocation.generateDisplayLists(4);

	public CulledListChunk(World w, RenderGlobal rg, int i) {
		super(w, rg, i);
	}

	@Override
	public void setCullFunc(CulledVboChunk.CullFunc fn) {
		cullFunc = fn;
		if (fn == null) {
			cullFunc = CulledVboChunk.noCull;
			if (wasCulled) setNeedsUpdate(false);
		}
	}

	ChunkCache ofWorldView;

	@Override
	public void setOfWorldView(ChunkCache cc) {
		ofWorldView = cc;
	}

	@Override
	public ChunkCache getOfWorldView() {
		return ofWorldView;
	}


	public int getDisplayList(BlockRenderLayer layer, CompiledChunk cc) {
		return !cc.isLayerEmpty(layer) ? displayLists + layer.ordinal() : -1;
	}

	@Override
	public void rebuildChunk(float x, float y, float z, ChunkCompileTaskGenerator generator) {
		wasCulled = CulledVboChunk.rebuildChunk0(this, x, y, z, generator, cullFunc);
	}

	public void deleteGlResources() {
		super.deleteGlResources();
		GLAllocation.deleteDisplayLists(displayLists, 4);
	}
}
