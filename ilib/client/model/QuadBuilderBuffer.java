package ilib.client.model;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;

import java.nio.ByteBuffer;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
//!!AT [["net.minecraft.client.renderer.BufferBuilder", ["field_178997_d"]]]
public class QuadBuilderBuffer extends BufferBuilder {
	public QuadBuilder builder;

	public QuadBuilderBuffer() {
		super(256);
	}

	@Override
	public void begin(int glMode, VertexFormat format) {
		super.begin(glMode, format);
		builder.begin(format, glMode == GL11.GL_TRIANGLES, builder.target);
	}

	@Override
	public void setVertexState(State state) {
		throw new UnsupportedOperationException();
	}

	@Override
	public State getVertexState() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void addVertexData(int[] data) {
		super.addVertexData(data);

		int len = vertexCount;
		int once = (builder.triangle ? 3 : 4);
		ByteBuffer bb = getByteBuffer();

		int vfLen = getVertexFormat().getSize();
		int offset = 0;
		while (len > once) {
			bb.position(offset).limit(offset + vfLen);
			bb.get(builder.getBuffer().list);
			bb.clear();

			builder.target.add(builder.bake());
			len -= once;
			offset += vfLen;
		}
		vertexCount = len;

		bb.limit(bb.limit() + len * vfLen);
		bb.compact();
	}

	@Override
	public BufferBuilder normal(float x, float y, float z) {
		builder.normal1(x, y, z);
		return super.normal(x, y, z);
	}

	@Override
	public void putNormal(float x, float y, float z) {
		builder.normal1(x, y, z);
		super.putNormal(x, y, z);
	}

	@Override
	public void endVertex() {
		super.endVertex();

		if (getVertexCount() == (builder.triangle ? 3 : 4)) {
			ByteBuffer bb = getByteBuffer();
			bb.position(0).limit(getVertexFormat().getSize());
			bb.get(builder.getBuffer().list);
			bb.clear();
			builder.target.add(builder.bake());
			builder.sprite = QuadBuilder.MISSING;
			vertexCount = 0;
		}
	}

	public QuadBuilderBuffer sprite(TextureAtlasSprite sprite) {
		builder.sprite = sprite;
		return this;
	}
}