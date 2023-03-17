package ilib.client.model;

import org.apache.commons.lang3.tuple.Pair;
import roj.collect.SimpleList;
import roj.util.Helpers;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;

import javax.vecmath.Matrix4f;
import java.util.Collections;
import java.util.List;

/**
 * @author Roj234
 * @since 2020/9/5 20:13
 */
public class DynamicModel implements IBakedModel {
	private final List<BakedQuad>[] quads;
	private IBakedModel pair;

	public DynamicModel() {
		this.quads = Helpers.cast(new List<?>[7]);
		for (int i = 0; i < 7; i++) {
			quads[i] = new SimpleList<>();
		}
	}

	public List<BakedQuad>[] getQuads() {
		return quads;
	}

	public void clearQuads() {
		for (List<BakedQuad> quad : quads) {
			if (quad != null) quad.clear();
		}
	}

	public void copy(IBakedModel model) {
		pair = model;
		clearQuads();
	}

	public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long rand) {
		if (quads.length == 1) {
			if (side == null) {
				return quads[0];
			} else {
				return Collections.emptyList();
			}
		}
		List<BakedQuad> quad = quads[side == null ? 0 : side.ordinal() + 1];
		return quad == null ? Collections.emptyList() : quad;
	}

	public boolean isAmbientOcclusion() {
		return pair.isAmbientOcclusion();
	}

	public boolean isAmbientOcclusion(IBlockState state) {
		return pair.isAmbientOcclusion(state);
	}

	public boolean isGui3d() {
		return pair.isGui3d();
	}

	public boolean isBuiltInRenderer() {
		return pair.isBuiltInRenderer();
	}

	public TextureAtlasSprite getParticleTexture() {
		return pair.getParticleTexture();
	}

	public ItemOverrideList getOverrides() {
		return pair.getOverrides();
	}

	public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType ctt) {
		return pair.handlePerspective(ctt);
	}
}
