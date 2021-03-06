package ilib.asm.nixim.mock;

import ilib.client.mirror.render.world.CulledChunk;
import ilib.client.mirror.render.world.CulledVboChunk;
import ilib.misc.XRay;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;

@Nixim(value = "net.minecraft.client.renderer.chunk.RenderChunk", copyItf = true)
public class MockXRay extends RenderChunk implements CulledChunk {
    @Copy
    ChunkCache myWorldView;
    @Copy(staticInitializer = "init")
    static CulledVboChunk.CullFunc aaa;

    static void init() {
        aaa = (pos, state) -> {
            return XRay.shouldCulled(state);
        };
    }

    public MockXRay(World _lvt_1_, RenderGlobal _lvt_2_, int _lvt_3_) {
        super(_lvt_1_, _lvt_2_, _lvt_3_);
    }

    @Override
    @Inject("func_178581_b")
    public void rebuildChunk(float x, float y, float z, ChunkCompileTaskGenerator gen) {
        CulledVboChunk.rebuildChunk0(this, x, y, z, gen, aaa);
    }

    @Override
    public void setCullFunc(CulledVboChunk.CullFunc fn) {}

    @Override
    @Copy
    public void setOfWorldView(ChunkCache cc) {
        myWorldView = cc;
    }

    @Override
    @Copy
    public ChunkCache getOfWorldView() {
        return myWorldView;
    }
}
