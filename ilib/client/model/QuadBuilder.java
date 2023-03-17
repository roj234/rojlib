package ilib.client.model;

import ilib.client.RenderUtils;
import roj.math.Vec4f;
import roj.util.ByteList;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.EnumFacing;

import java.util.List;

/**
 * @author Roj233
 * @since 2022/5/12 23:26
 */
public class QuadBuilder extends QuadMutator {
	private int vertexCount;
	public List<BakedQuad> target;

	static TextureAtlasSprite MISSING;

	@Override
	public void setVertex(BakedQuad from) {
		throw new RuntimeException("use setModeNew()");
	}

	public void begin(VertexFormat fmt, boolean triangle, List<BakedQuad> target) {
		MISSING = RenderUtils.TEXMAP_BLOCK.getMissingSprite();

		tintIndex = -1;
		diffuseLighting = false;
		sprite = MISSING;
		facing = null;
		normal.x = Float.NaN;

		setVertexFormat(fmt);

		ByteList list = data;
		list.clear();
		list.ensureCapacity(len);

		this.target = target;
		this.triangle = triangle;
	}

	public QuadBuilder diffuseLight(boolean diffuse) {
		diffuseLighting = diffuse;
		return this;
	}

	public QuadBuilder sprite(TextureAtlasSprite sprite) {
		this.sprite = sprite;
		return this;
	}

	public QuadBuilder tint(int tint) {
		this.tintIndex = tint;
		return this;
	}

	public QuadBuilder normal(EnumFacing facing) {
		this.facing = facing;
		return this;
	}

	public QuadBuilder uv(float u, float v) {
		int off = uvOff + vertexCount * len;
		data.putFloat(off, u).putFloat(off + 4, v);
		return this;
	}

	public QuadBuilder pos(float x, float y, float z) {
		Vec4f tmp = this.off;
		rotation.mul(tmp.set(x, y, z), tmp);

		int off = posOff + vertexCount * len;
		data.putFloat(off, tmp.x).putFloat(off + 4, tmp.y).putFloat(off + 8, tmp.z);
		return this;
	}

	public QuadBuilder lightmap(int skyLight, int blockLight) {
		int off = lmapOff + vertexCount * len;
		if (off < 0) throw new IllegalStateException("No LMAP");
		int len = this.len;
		switch (lmapType) {
			case 2:
				data.putShort(off, skyLight).putShort(off + 2, blockLight);
				break;
			case 1:
				data.put(off, (byte) skyLight).put(off + 1, (byte) blockLight);
				break;
		}
		return this;
	}

	public QuadBuilder color(int rgba) {
		data.putInt(colorOff + vertexCount * len, rgba);
		return this;
	}

	public ByteList getBuffer() {
		return data;
	}

	public void endVertex() {
		int v = vertexCount + 1;
		if (v == (triangle ? 3 : 4)) {
			vertexCount = 0;
			target.add(bake());
		} else {
			vertexCount = v;
		}
	}

	public static TextureAtlasSprite getSprite(String key) {
		return RenderUtils.TEXMAP_BLOCK.getAtlasSprite(key);
	}

	public static TextureAtlasSprite getSpriteForUV(float u, float v) {
		//        for (TextureAtlasSprite sprite : RenderUtils.TEXMAP_BLOCK.mapUploadedSprites.values()) {
		//            if (u >= sprite.getMinU() && u <= sprite.getMaxU() &&
		//                v >= sprite.getMinV() && v <= sprite.getMaxV())
		//                return sprite;
		//        }
		return MISSING;
	}
}
