package ilib.asm.nixim.mock;

import ilib.client.RenderUtils;
import ilib.misc.XRay;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.ListedRenderChunk;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.fml.client.FMLClientHandler;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.collect.MyHashSet;

import java.util.Iterator;
import java.util.Set;

import static ilib.asm.util.MCHooks.BlockRenderLayerValues;

@Nixim("net.minecraft.client.renderer.chunk.RenderChunk")
public class MockXRay extends ListedRenderChunk {
    public MockXRay(World _lvt_1_, RenderGlobal _lvt_2_, int _lvt_3_) {
        super(_lvt_1_, _lvt_2_, _lvt_3_);
    }

    @Override
    @Inject("func_178581_b")
    public void rebuildChunk(float x, float y, float z, ChunkCompileTaskGenerator generator) {
        CompiledChunk cc = new CompiledChunk();
        int i = 1;
        BlockPos minPos = position;
        BlockPos maxPos = minPos.add(15, 15, 15);

        generator.getLock().lock();

        try {
            if (generator.getStatus() != ChunkCompileTaskGenerator.Status.COMPILING) {
                return;
            }

            generator.setCompiledChunk(cc);
        } finally {
            generator.getLock().unlock();
        }

        VisGraph graph = new VisGraph();
        Set<TileEntity> globalTiles = new MyHashSet<>();

        ChunkCache worldView = FMLClientHandler.instance().hasOptifine() ? createRegionRenderCache(Minecraft.getMinecraft().world, this.position.add(-1, -1, -1), this.position.add(16, 16, 16), 1) : this.worldView;
        if (!worldView.isEmpty()) {
            ++renderChunksUpdated;

            int layerUsed = 0;

            BlockRendererDispatcher dispatcher = RenderUtils.BLOCK_RENDERER;

            for (BlockPos.MutableBlockPos pos : BlockPos.getAllInBoxMutable(minPos, maxPos)) {
                IBlockState state = worldView.getBlockState(pos);
                Block block = state.getBlock();
                boolean render = XRay.shouldBlockBeRendered(state);

                if (state.isOpaqueCube()) {
                    graph.setOpaqueCube(pos);
                }

                if (!render) {
                    continue;
                } else if (block.hasTileEntity(state)) {
                    TileEntity tile = worldView.getTileEntity(pos, Chunk.EnumCreateEntityType.CHECK);

                    if (tile != null) {
                        TileEntitySpecialRenderer<TileEntity> tesr = TileEntityRendererDispatcher.instance.getRenderer(tile);

                        if (tesr != null) {
                            cc.addTileEntity(tile);

                            if (tesr.isGlobalRenderer(tile)) {
                                globalTiles.add(tile);
                            }
                        }
                    }
                }

                BlockRenderLayer[] values = BlockRenderLayerValues;
                for (int j = 0; j < values.length; j++) {
                    BlockRenderLayer layer = values[j];
                    if (!block.canRenderInLayer(state, layer)) {
                        continue;
                    }
                    ForgeHooksClient.setRenderLayer(layer);

                    if (block.getDefaultState().getRenderType() != EnumBlockRenderType.INVISIBLE) {
                        BufferBuilder bufferbuilder = generator.getRegionRenderCacheBuilder().getWorldRendererByLayerId(j);

                        if (!cc.isLayerStarted(layer)) {
                            cc.setLayerStarted(layer);
                            preRenderBlocks(bufferbuilder, minPos);
                        }

                        if (dispatcher.renderBlock(state, pos, worldView, bufferbuilder)) {
                            layerUsed |= 1 << j;
                        }
                    }
                }
                ForgeHooksClient.setRenderLayer(null);
            }

            for (BlockRenderLayer layer : BlockRenderLayerValues) {
                if ((layerUsed & (1 << layer.ordinal())) != 0) {
                    cc.setLayerUsed(layer);
                }

                if (cc.isLayerStarted(layer)) {
                    postRenderBlocks(layer, x, y, z, generator.getRegionRenderCacheBuilder().getWorldRendererByLayer(layer), cc);
                }
            }
        }

        cc.setVisibility(graph.computeVisibility());
        lockCompileTask.lock();

        try {
            Set<TileEntity> toAdd = new MyHashSet<>();
            Set<TileEntity> toRemove = new MyHashSet<>();

            for (Iterator<TileEntity> itr = setTileEntities.iterator(); itr.hasNext(); ) {
                TileEntity tile = itr.next();
                if (!globalTiles.contains(tile)) {
                    toRemove.add(tile);
                    itr.remove();
                }
            }

            for (TileEntity tile : globalTiles) {
                if (!setTileEntities.remove(tile)) {
                    toAdd.add(tile);
                }
            }

            setTileEntities = globalTiles;

            renderGlobal.updateTileEntities(toRemove, toAdd);
        } finally {
            lockCompileTask.unlock();
        }
    }
}
