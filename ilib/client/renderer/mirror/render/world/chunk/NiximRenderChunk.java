/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ilib.client.renderer.mirror.render.world.chunk;

import ilib.client.util.RenderUtils;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.collect.MyHashSet;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
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
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.fml.client.FMLClientHandler;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Set;

// fixme crashes with optifine
//@Nixim("net.minecraft.client.renderer.chunk.RenderChunk")
public class NiximRenderChunk extends ListedRenderChunk implements MyRenderChunk {
    @Copy
    public BlockPos pos;
    @Copy
    public EnumFacing face;
    @Copy
    public boolean noCull, wasCulled;
    @Copy
    public ChunkCache optiWorldView;

    @Copy
    private ChunkCompileTaskGenerator optiCompileTask;

    @Inject("<init>")
    public NiximRenderChunk(World worldIn, RenderGlobal renderGlobalIn, int indexIn) {
        super(worldIn, renderGlobalIn, indexIn);
        this.pos = BlockPos.ORIGIN;
        this.face = EnumFacing.UP;
    }

    @Override
    @Copy
    public void setCurrentPositionsAndFaces(BlockPos pos, EnumFacing face) {
        this.pos = pos;
        this.face = face;
    }

    @Override
    @Copy
    public void setNoCull(boolean flag) {
        if (flag && !noCull && wasCulled) {
            setNeedsUpdate(false);
        }
        this.noCull = flag;
    }

    @Override
    @Inject("func_178581_b")
    public void rebuildChunk(float x, float y, float z, ChunkCompileTaskGenerator generator) {
        wasCulled = false;
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

        BlockRenderLayer[] values = BlockRenderLayer.values();

        ChunkCache worldView = getWorldView();
        if (!worldView.isEmpty()) {
            ++renderChunksUpdated;

            boolean[] layerUsed = new boolean[BlockRenderLayer.values().length];

            BlockRendererDispatcher dispatcher = RenderUtils.BLOCK_RENDERER;

            Ivsb func = null;
            BlockPos pos00 = pos;
            switch (face) {
                case NORTH:
                    func = (pos1) -> pos1.getZ() < pos00.getZ();
                    break;
                case SOUTH:
                    func = (pos1) -> pos1.getZ() > pos00.getZ();
                    break;
                case WEST:
                    func = (pos1) -> pos1.getX() < pos00.getX();
                    break;
                case EAST:
                    func = (pos1) -> pos1.getX() > pos00.getX();
                    break;
                case UP:
                    func = (pos1) -> pos1.getY() > pos00.getY();
                    break;
                case DOWN:
                    func = (pos1) -> pos1.getY() < pos00.getY();
                    break;
            }

            for (BlockPos.MutableBlockPos pos : BlockPos.getAllInBoxMutable(minPos, maxPos)) {
                boolean noRender = !noCull && func.apply(pos);

                IBlockState state = worldView.getBlockState(pos);
                Block block = state.getBlock();

                if (state.isOpaqueCube()) {
                    graph.setOpaqueCube(pos);
                }

                if (noRender) {
                    wasCulled = true;
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

                for (int j = 0, valuesLength = values.length; j < valuesLength; j++) {
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

                        if (!noRender && dispatcher.renderBlock(state, pos, worldView, bufferbuilder)) {
                            layerUsed[j] = true;
                        }
                    }
                }
                ForgeHooksClient.setRenderLayer(null);
            }

            for (BlockRenderLayer layer : values) {
                if (layerUsed[layer.ordinal()]) {
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

            for (Iterator<TileEntity> itr = globalTiles.iterator(); itr.hasNext(); ) {
                TileEntity tile = itr.next();
                if (setTileEntities.add(tile)) {
                    toAdd.add(tile);
                    itr.remove();
                }
            }

            renderGlobal.updateTileEntities(toRemove, toAdd);
        } finally {
            lockCompileTask.unlock();
        }
    }

    @Copy
    public ChunkCache getWorldView() {
        if (FMLClientHandler.instance().hasOptifine()) {
            return optiWorldView;
        } else {
            return worldView;
        }
    }

    @Override
    @Inject("func_178578_b")
    protected void finishCompileTask() {
        if (!Ivsb.hasOptifine) {
            super.finishCompileTask();
            return;
        }

        this.lockCompileTask.lock();

        try {
            if (this.optiCompileTask != null && this.optiCompileTask.getStatus() != ChunkCompileTaskGenerator.Status.DONE) {
                this.optiCompileTask.finish();
                this.optiCompileTask = null;
            }
        } finally {
            this.lockCompileTask.unlock();
        }
    }

    @Override
    @Inject("func_178574_d")
    public ChunkCompileTaskGenerator makeCompileTaskChunk() {
        if (!Ivsb.hasOptifine) {
            return super.makeCompileTaskChunk();
        }

        this.lockCompileTask.lock();
        ChunkCompileTaskGenerator chunkcompiletaskgenerator;

        try {
            this.finishCompileTask();
            this.optiCompileTask = new ChunkCompileTaskGenerator(this, ChunkCompileTaskGenerator.Type.REBUILD_CHUNK, this.getDistanceSq());

            ChunkCache cache = createRegionRenderCache(this.world, this.position.add(-1, -1, -1), this.position.add(16, 16, 16), 1);
            MinecraftForgeClient.onRebuildChunk(this.world, this.position, cache);
            this.optiWorldView = cache;

            chunkcompiletaskgenerator = this.optiCompileTask;
        } finally {
            this.lockCompileTask.unlock();
        }

        return chunkcompiletaskgenerator;
    }

    @Nullable
    @Override
    @Inject("func_178582_e")
    public ChunkCompileTaskGenerator makeCompileTaskTransparency() {
        if (!Ivsb.hasOptifine) {
            return super.makeCompileTaskTransparency();
        }

        lockCompileTask.lock();
        ChunkCompileTaskGenerator gen;

        try {
            ChunkCompileTaskGenerator task = optiCompileTask;
            if (task == null || task.getStatus() != ChunkCompileTaskGenerator.Status.PENDING) {
                if (task != null && task.getStatus() != ChunkCompileTaskGenerator.Status.DONE) {
                    task.finish();
                }

                task = new ChunkCompileTaskGenerator(this, ChunkCompileTaskGenerator.Type.RESORT_TRANSPARENCY, getDistanceSq());

                optiCompileTask = task;

                task.setCompiledChunk(compiledChunk);
                gen = task;
                return gen;
            }
        } finally {
            lockCompileTask.unlock();
        }

        return null;
    }
}
