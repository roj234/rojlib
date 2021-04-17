package ilib.asm.nx.client.crd;

import ilib.Config;
import ilib.asm.util.MCHooksClient;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.color.BlockColors;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import net.minecraftforge.client.model.pipeline.VertexBufferConsumer;
import net.minecraftforge.client.model.pipeline.VertexLighterFlat;
import net.minecraftforge.client.model.pipeline.VertexLighterSmoothAo;
import net.minecraftforge.common.ForgeModContainer;

import java.util.Collections;
import java.util.List;

/**
 * @author Roj233
 * @since 2022/4/22 19:09
 */
@Nixim("net.minecraftforge.client.model.pipeline.ForgeBlockModelRenderer")
class NxMem2 extends BlockModelRenderer {

	@Inject("/")
	public static boolean render(VertexLighterFlat lighter, IBlockAccess world, IBakedModel model, IBlockState state, BlockPos pos, BufferBuilder wr, boolean checkSides, long rand) {
		lighter.setWorld(world);
		lighter.setState(state);
		lighter.setBlockPos(pos);

		boolean empty = true;

		List<BakedQuad> quads = model.getQuads(state, null, rand);
		if (!quads.isEmpty()) {
			lighter.updateBlockInfo();
			empty = false;
			for (int i = 0; i < quads.size(); i++) {
				quads.get(i).pipe(lighter);
			}
		}

		for (EnumFacing side : EnumFacing.VALUES) {
			quads = model.getQuads(state, side, rand);
			if (!quads.isEmpty() && (!checkSides || state.shouldSideBeRendered(world, pos, side))) {
				if (empty) {
					lighter.updateBlockInfo();
				}

				empty = false;
				for (int i = 0; i < quads.size(); i++) {
					quads.get(i).pipe(lighter);
				}
			}
		}

		lighter.resetBlockInfo();
		return !empty;
	}

	@Shadow("/")
	private ThreadLocal<VertexLighterFlat> lighterFlat;
	@Shadow("/")
	private ThreadLocal<VertexLighterSmoothAo> lighterSmooth;
	@Shadow("/")
	private ThreadLocal<VertexBufferConsumer> consumerFlat;
	@Shadow("/")
	private ThreadLocal<VertexBufferConsumer> consumerSmooth;

	public NxMem2(BlockColors blockColorsIn) {
		super(blockColorsIn);
	}

	@Inject(value = "/", at = Inject.At.REPLACE)
	public boolean renderModelFlat(IBlockAccess world, IBakedModel model, IBlockState state, BlockPos pos, BufferBuilder buffer, boolean checkSides, long rand) {
		if (ForgeModContainer.forgeLightPipelineEnabled) {
			float hd = isHDModel(checkSides ? world : null, model, state, pos, rand);
			if (hd != hd) return false;
			if (hd > Config.complexModelThreshold) {
				VertexBufferConsumer c = consumerFlat.get();
				c.setBuffer(buffer);
				c.setOffset(pos);

				VertexLighterFlat l = lighterFlat.get();
				l.setParent(c);
				l.setWorld(world);
				l.setState(state);
				l.setBlockPos(pos);
				return render(l);
			}
		}
		return super.renderModelFlat(world, model, state, pos, buffer, checkSides, rand);
	}

	@Inject(value = "/", at = Inject.At.REPLACE)
	public boolean renderModelSmooth(IBlockAccess world, IBakedModel model, IBlockState state, BlockPos pos, BufferBuilder buffer, boolean checkSides, long rand) {
		if (ForgeModContainer.forgeLightPipelineEnabled) {
			float hd = isHDModel(checkSides ? world : null, model, state, pos, rand);
			if (hd != hd) return false;
			if (hd > Config.complexModelThreshold) {
				VertexBufferConsumer c = consumerSmooth.get();
				c.setBuffer(buffer);
				c.setOffset(pos);

				VertexLighterSmoothAo l = lighterSmooth.get();
				l.setParent(c);
				l.setWorld(world);
				l.setState(state);
				l.setBlockPos(pos);
				return render(l);
			}
		}
		return super.renderModelSmooth(world, model, state, pos, buffer, checkSides, rand);
	}

	@Copy
	private static float isHDModel(IBlockAccess world, IBakedModel model, IBlockState state, BlockPos pos, long rand) {
		List<BakedQuad>[] arr = MCHooksClient.get().tmpArray;

		int quadCount = 0, faceCount = 0;

		List<BakedQuad> quads = model.getQuads(state, null, rand);
		arr[0] = quads;
		if (!quads.isEmpty()) {
			faceCount++;
			quadCount += quads.size();
		}

		EnumFacing[] values = EnumFacing.VALUES;
		for (int i = 0; i < values.length; ) {
			EnumFacing side = values[i];
			if (world == null || state.shouldSideBeRendered(world, pos, side)) {
				arr[++i] = quads = model.getQuads(state, side, rand);
				if (!quads.isEmpty()) {
					faceCount++;
					quadCount += quads.size();
				}
			} else {
				arr[++i] = Collections.emptyList();
			}
		}

		return quadCount / (float) faceCount;
	}

	@Copy
	private static boolean render(VertexLighterFlat l) {
		l.updateBlockInfo();

		List<BakedQuad>[] arr = MCHooksClient.get().tmpArray;

		for (int i = 0; i < 7; i++) {
			List<BakedQuad> quads = arr[i];
			for (int j = 0; j < quads.size(); j++) {
				quads.get(j).pipe(l);
			}
		}

		l.resetBlockInfo();
		return true;
	}
}
