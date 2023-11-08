package roj.render.vertex;

import roj.io.NIOUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static roj.render.vertex.VertexFormat.*;

/**
 * Vertex Builder
 *
 * @author Roj233
 * @since 2021/9/18 13:15
 */
public class VertexBuilder {
	private ByteBuffer buf;
	private int vertexCount;

	private VertexFormat format;
	private VertexFormat.Entry entry;
	private int formatIndex;
	private int offset;

	public boolean noColor;
	public double xOffset, yOffset, zOffset;
	public double xScale, yScale, zScale;

	static final int MAX_BUFFER_CAPACITY = 1024 * 1024 * 8;

	public VertexBuilder(int initialCapacity) {
		buf = ByteBuffer.allocateDirect(initialCapacity).order(ByteOrder.nativeOrder());
	}

	public void grow(int plus) {
		if (buf.capacity() - getBufferSize() - offset - plus < 0) {
			if (buf.capacity() < MAX_BUFFER_CAPACITY) {
				int newCap = buf.capacity() + roundUp(plus, 262144);
				ByteBuffer newBuf = ByteBuffer.allocateDirect(newCap).order(ByteOrder.nativeOrder());
				buf.flip();
				newBuf.put(buf);
				NIOUtil.clean(buf);
				buf = newBuf;
			} else {
				vertexCount = 0;
			}
		}
	}

	private static int roundUp(int num, int align) {
		return ((num % align > 0 ? 1 : 0) + num / align) * align;
	}

	public int getBufferSize() {
		return vertexCount * format.getSize();
	}

	public void reset() {
		vertexCount = 0;
		entry = format.getEntry(formatIndex = 0);
		offset = 0;
		noColor = false;
	}

	public void begin(VertexFormat format) {
		this.format = format;
		reset();
		buf.position(0).limit(buf.capacity());
	}

	public void end() {
		buf.position(0);
		buf.limit(getBufferSize());
		noColor = false;
		xOffset = yOffset = zOffset = 0;
	}

	public void putVertexes(byte[] data) {
		if (data.length % format.getSize() != 0) {
			throw new IllegalArgumentException("Non padded");
		}
		grow(data.length + format.getSize());
		buf.position(getBufferSize());
		buf.put(data);
		vertexCount += data.length / format.getSize();
	}

	public void putVertexes(ByteBuffer buffer) {
		if (buffer.limit() % format.getSize() != 0) {
			throw new IllegalArgumentException("Non padded");
		}
		grow(buffer.limit() + format.getSize());
		buf.position(getBufferSize());
		buf.put(buffer);
		vertexCount += buffer.limit() / format.getSize();
	}

	public VertexBuilder pos(double x, double y, double z) {
		int i = vertexCount * format.getSize() + offset;
		switch (entry.type()) {
			case FLOAT:
				buf.putFloat(i, (float) (x + xOffset))
				   .putFloat(i + 4, (float) (y + yOffset))
				   .putFloat(i + 8, (float) (z + zOffset));
				break;
			case UINT:
			case INT:
				buf.putInt(i, (int) (x + xOffset))
				   .putInt(i + 4, (int) (y + yOffset))
				   .putInt(i + 8, (int) (z + zOffset));
				break;
			case USHORT:
			case SHORT:
				buf.putShort(i, (short) (x + xOffset))
				   .putShort(i + 2, (short) (y + yOffset))
				   .putShort(i + 4, (short) (z + zOffset));
				break;
			case UBYTE:
			case BYTE:
				buf.put(i, (byte) (x + xOffset))
				   .put(i + 1, (byte) (y + yOffset))
				   .put(i + 2, (byte) (z + zOffset));
				break;
		}

		next();
		return this;
	}

	public VertexBuilder pos(double x, double y) {
		int i = vertexCount * format.getSize() + offset;
		switch (entry.type()) {
			case FLOAT:
				buf.putFloat(i, (float) (x + xOffset))
				   .putFloat(i + 4, (float) (y + yOffset));
				break;
			case UINT:
			case INT:
				buf.putInt(i, (int) (x + xOffset))
				   .putInt(i + 4, (int) (y + yOffset));
				break;
			case USHORT:
			case SHORT:
				buf.putShort(i, (short) (x + xOffset))
				   .putShort(i + 2, (short) (y + yOffset));
				break;
			case UBYTE:
			case BYTE:
				buf.put(i, (byte) (x + xOffset))
				   .put(i + 1, (byte) (y + yOffset));
				break;
		}

		next();
		return this;
	}

	public VertexBuilder tex(double u, double v) {
		int i = vertexCount * format.getSize() + offset;
		switch (entry.type()) {
			case FLOAT:
				buf.putFloat(i, (float) u)
				   .putFloat(i + 4, (float) v);
				break;
			case UINT:
			case INT:
				buf.putInt(i, (int) u)
				   .putInt(i + 4, (int) v);
				break;
			case USHORT:
			case SHORT:
				buf.putShort(i, (short) v)
				   .putShort(i + 2, (short) u);
				break;
			case UBYTE:
			case BYTE:
				buf.put(i, (byte) v)
				   .put(i + 1, (byte) u);
				break;
		}

		next();
		return this;
	}

	public VertexBuilder colorf(float rF, float gF, float bF, float aF) {
		return color((int) (rF * 255.0F), (int) (gF * 255.0F), (int) (bF * 255.0F), (int) (aF * 255.0F));
	}
	public VertexBuilder color(int argb) {
		return color((argb >>> 16) & 0xFF, (argb >>> 8) & 0xFF, (argb) & 0xFF, (argb >>> 24) & 0xFF);
	}
	public VertexBuilder color(int r, int g, int b, int a) {
		if (!noColor) {
			int i = vertexCount * format.getSize() + offset;
			switch (entry.type()) {
				case FLOAT:
					buf.putFloat(i, r / 255.0F)
					   .putFloat(i + 4, g / 255.0F)
					   .putFloat(i + 8, b / 255.0F)
					   .putFloat(i + 12, a / 255.0F);
					break;
				case UINT:
				case INT:
					buf.putInt(i, r)
					   .putInt(i + 4, g)
					   .putInt(i + 8, b)
					   .putInt(i + 12, a);
					break;
				case USHORT:
				case SHORT:
					buf.putShort(i, (short) r)
					   .putShort(i + 2, (short) g)
					   .putShort(i + 4, (short) b)
					   .putShort(i + 6, (short) a);
					break;
				case UBYTE:
				case BYTE:
					if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
						buf.put(i, (byte) r)
						   .put(i + 1, (byte) g)
						   .put(i + 2, (byte) b)
						   .put(i + 3, (byte) a);
					} else {
						buf.put(i, (byte) a)
						   .put(i + 1, (byte) b)
						   .put(i + 2, (byte) g)
						   .put(i + 3, (byte) r);
					}
					break;
			}
		}
		next();
		return this;
	}

	public VertexBuilder alpha(float alpha) {
		return alpha((int) (alpha * 255.0F));
	}

	public VertexBuilder alpha(int alpha) {
		if (!noColor) {
			int i = vertexCount * format.getSize() + offset;
			switch (entry.type()) {
				case FLOAT:
					buf.putFloat(i, alpha / 255.0F);
					break;
				case UINT:
				case INT:
					buf.putInt(i, alpha);
					break;
				case USHORT:
				case SHORT:
					buf.putShort(i, (short) alpha);
					break;
				case UBYTE:
				case BYTE:
					buf.put(i, (byte) alpha);
					break;
			}
		}
		next();
		return this;
	}

	public VertexBuilder normal(float x, float y, float z) {
		int i = vertexCount * format.getSize() + offset;
		switch (entry.type()) {
			case FLOAT:
				buf.putFloat(i, x)
				   .putFloat(i + 4, y)
				   .putFloat(i + 8, z);
				break;
			case UINT:
			case INT:
				buf.putInt(i, (int) x)
				   .putInt(i + 4, (int) y)
				   .putInt(i + 8, (int) z);
				break;
			case USHORT:
			case SHORT:
				buf.putShort(i, (short) ((int) (x * 32767.0F) & 0xFFFF))
				   .putShort(i + 2, (short) ((int) (y * 32767.0F) & 0xFFFF))
				   .putShort(i + 4, (short) ((int) (z * 32767.0F) & 0xFFFF));
				break;
			case UBYTE:
			case BYTE:
				buf.put(i, (byte) ((int) (x * 127.0F) & 0xFF))
				   .put(i + 1, (byte) ((int) (y * 127.0F) & 0xFF))
				   .put(i + 2, (byte) ((int) (z * 127.0F) & 0xFF));
				break;
		}

		next();
		return this;
	}

	public void endVertex() {
		offset = 0;
		vertexCount++;
		entry = format.getEntry(formatIndex = 0);
		grow(format.getSize());
	}

	public void next() {
		do {
			if (++formatIndex == format.entryCount()) {
				offset = 0;
				formatIndex = 0;
			} else {
				offset += entry.totalSize();
			}
			entry = format.getEntry(formatIndex);
		} while (entry.usage() == PADDING);
	}

	public VertexBuilder translate(double x, double y, double z) {
		xOffset = x;
		yOffset = y;
		zOffset = z;
		return this;
	}

	public ByteBuffer getBuffer() {
		return buf;
	}

	public VertexFormat getVertexFormat() {
		return format;
	}

	public int getVertexCount() {
		return vertexCount;
	}

	public void free() {
		if (buf != null) {
			NIOUtil.clean(buf);
			buf = null;
		}
	}
}