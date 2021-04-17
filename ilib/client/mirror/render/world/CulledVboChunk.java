package ilib.client.mirror.render.world;

import ilib.asm.util.MCHooksClient;
import ilib.client.RenderUtils;
import roj.collect.MyHashSet;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.chunk.ChunkCompileTaskGenerator;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
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

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

public class CulledVboChunk extends RenderChunk implements CulledChunk {
	CullFunc cullFunc = noCull;
	boolean wasCulled;

	public CulledVboChunk(World w, RenderGlobal rg, int i) {
		super(w, rg, i);
	}

	@Override
	public void setCullFunc(CullFunc fn) {
		cullFunc = fn;
		if (fn == null) {
			cullFunc = noCull;
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

	@Override
	public void rebuildChunk(float x, float y, float z, ChunkCompileTaskGenerator gen) {
		wasCulled = rebuildChunk0(this, x, y, z, gen, cullFunc);
	}

	public static boolean rebuildChunk0(RenderChunk $this, float x, float y, float z, ChunkCompileTaskGenerator gen, CullFunc func) {
		CompiledChunk cc = new CompiledChunk();
		gen.getLock().lock();
		try {
			if (gen.getStatus() != ChunkCompileTaskGenerator.Status.COMPILING) return false;
			gen.setCompiledChunk(cc);
		} finally {
			gen.getLock().unlock();
		}

		boolean wasCulled = false;
		MyVisGraph graph = MCHooksClient.get().graph;
		graph.clear();
		Set<TileEntity> globalTiles = Collections.emptySet();

		ChunkCache view = CulledChunk.getWorldView($this);
		if (!view.isEmpty()) {
			++renderChunksUpdated;

			int layerUsed = 0;

			BlockRendererDispatcher dsp = RenderUtils.BLOCK_RENDERER;

			BlockPos min = $this.position;
			for (BlockPos.MutableBlockPos pos : BlockPos.getAllInBoxMutable(min, min.add(15, 15, 15))) {
				IBlockState state = view.getBlockState(pos);
				Block block = state.getBlock();

				if (state.isOpaqueCube()) {
					graph.setOpaqueCube(pos);
				}

				boolean skip = func.test(pos, state);
				if (skip) {
					wasCulled = true;
					continue;
				} else if (block.hasTileEntity(state)) {
					TileEntity tile = view.getTileEntity(pos, Chunk.EnumCreateEntityType.CHECK);

					if (tile != null) {
						TileEntitySpecialRenderer<TileEntity> tesr = TileEntityRendererDispatcher.instance.getRenderer(tile);

						if (tesr != null) {
							cc.addTileEntity(tile);

							if (tesr.isGlobalRenderer(tile)) {
								if (globalTiles.isEmpty()) globalTiles = new MyHashSet<>();
								globalTiles.add(tile);
							}
						}
					}
				}

				BlockRenderLayer[] brls = MCHooksClient.BlockRenderLayerValues;
				for (int j = 0; j < brls.length; j++) {
					BlockRenderLayer layer = brls[j];
					if (!block.canRenderInLayer(state, layer)) continue;

					ForgeHooksClient.setRenderLayer(layer);

					if (block.getDefaultState().getRenderType() != EnumBlockRenderType.INVISIBLE) {
						BufferBuilder bb = gen.getRegionRenderCacheBuilder().getWorldRendererByLayerId(j);

						if (!cc.isLayerStarted(layer)) {
							cc.setLayerStarted(layer);
							$this.preRenderBlocks(bb, min);
						}

						if (dsp.renderBlock(state, pos, view, bb)) {
							layerUsed |= 1 << j;
						}
					}
				}
				ForgeHooksClient.setRenderLayer(null);
			}

			BlockRenderLayer[] brls = MCHooksClient.BlockRenderLayerValues;
			for (int j = 0; j < brls.length; j++) {
				BlockRenderLayer layer = brls[j];
				if ((layerUsed & (1 << j)) != 0) {
					cc.setLayerUsed(layer);
				}

				if (cc.isLayerStarted(layer)) {
					$this.postRenderBlocks(layer, x, y, z, gen.getRegionRenderCacheBuilder().getWorldRendererByLayer(layer), cc);
				}
			}
		}

		cc.setVisibility(graph.compute());

		if (globalTiles.isEmpty() && $this.setTileEntities.isEmpty()) return wasCulled;

		$this.lockCompileTask.lock();

		try {
			Set<TileEntity> toAdd = new MyHashSet<>();
			Set<TileEntity> toRemove = new MyHashSet<>();

			for (Iterator<TileEntity> itr = $this.setTileEntities.iterator(); itr.hasNext(); ) {
				TileEntity tile = itr.next();
				if (!globalTiles.contains(tile)) {
					toRemove.add(tile);
					itr.remove();
				}
			}

			for (TileEntity tile : globalTiles) {
				if (!$this.setTileEntities.remove(tile)) {
					toAdd.add(tile);
				}
			}

			$this.setTileEntities = globalTiles;

			$this.renderGlobal.updateTileEntities(toRemove, toAdd);
		} finally {
			$this.lockCompileTask.unlock();
		}
		return wasCulled;
	}

	public void deleteGlResources() {
		this.stopCompileTask();
		this.world = null;

		for (int i = 0; i < 4; ++i) {
			if (this.vertexBuffers[i] != null) {
				this.vertexBuffers[i].deleteGlBuffers();
			}
		}
	}

	static final CullFunc noCull = (p, state) -> false;

	public interface CullFunc {
		boolean test(BlockPos pos, IBlockState state);
	}
}
