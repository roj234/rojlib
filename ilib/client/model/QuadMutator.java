package ilib.client.model;

import roj.math.Mat4x3f;
import roj.math.MathUtils;
import roj.math.Vec3f;
import roj.math.Vec4f;
import roj.util.ByteList;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * @author Roj233
 * @since 2022/5/12 21:40
 */
public class QuadMutator {
	private static final float HALF_PI = (float) (Math.PI / 2);

	private final BlockPos.MutableBlockPos tmp = new BlockPos.MutableBlockPos();
	private int other;

	final Mat4x3f rotation = new Mat4x3f(), modelview = new Mat4x3f();
	final Vec4f off = new Vec4f();

	final ByteList data = new ByteList(64);

	VertexFormat format;
	Vec3f normal = new Vec3f();
	int uvOff, posOff, colorOff, lmapOff, lmapType, len;

	boolean triangle;

	public int tintIndex;
	public boolean diffuseLighting;
	public TextureAtlasSprite sprite;
	public EnumFacing facing;

	public QuadMutator() {}

	public final Mat4x3f setMatrix(EnumFacing face) {
		Mat4x3f rot = rotation.makeIdentity();
		Mat4x3f model = modelview.makeIdentity().translate(0.5f, 0.5f, 0.5f);
		switch (face) {
			case DOWN:
				rot.rotateY(HALF_PI * 2).rotateX(-HALF_PI);
				break;
			case UP:
				rot.rotateY(HALF_PI * 2).rotateX(HALF_PI);
				break;
			default:
			case NORTH:
				break;
			case SOUTH:
				rot.rotateY(-HALF_PI * 2);
				break;
			case WEST:
				rot.rotateY(HALF_PI);
				break;
			case EAST:
				rot.rotateY(-HALF_PI);
				break;
		}
		return model.mul(rot).translate(-0.5f, -0.5f, -0.5f);
	}

	public final boolean setBlock(IBlockAccess world, BlockPos pos, IBlockState state) {
		int flag = 0;

		Mat4x3f rot = rotation;
		BlockPos.MutableBlockPos tmp = this.tmp;
		Vec4f off = this.off;
		for (int x = -1; x <= 1; x++) {
			for (int y = -1; y <= 1; y++) {
				if ((x | y) == 0) continue;
				rot.mul(off.set(x, y), off);

				// 精度问题
				if (Math.abs(off.x) < 0.1) off.x = 0;
				if (Math.abs(off.y) < 0.1) off.y = 0;
				if (Math.abs(off.z) < 0.1) off.z = 0;

				if (world.getBlockState(tmp.setPos(pos.getX() + off.x, pos.getY() + off.y, pos.getZ() + off.z)) != state) {
					flag |= 1 << (4 + x * 3 + y);
				}
			}
		}
		this.other = flag;
		return (flag & 0b10101010) == 0b10101010;
	}

	public final boolean isOtherBlock(int x, int y) {
		return 0 != (other & (1 << (4 + x * 3 + y)));
	}

	public void setVertex(BakedQuad from) {
		tintIndex = from.getTintIndex();
		diffuseLighting = from.shouldApplyDiffuseLighting();
		sprite = from.getSprite();
		facing = from.getFace();
		normal.x = Float.NaN;

		ByteList list = this.data;
		list.clear();
		for (int data : from.getVertexData()) {
			list.putInt(data);
		}

		triangle = false;
		setVertexFormat(from.getFormat());
	}

	protected void setVertexFormat(VertexFormat fmt) {
		int uv = 0;

		uvOff = -1;
		lmapOff = -1;
		lmapType = -1;
		colorOff = -1;
		posOff = -1;
		len = fmt.getSize();

		for (int i = 0; i < fmt.getElementCount(); i++) {
			VertexFormatElement el = fmt.getElement(i);
			switch (el.getUsage()) {
				case UV:
					if (uv++ == 0) {
						if (el.getType() != VertexFormatElement.EnumType.FLOAT || el.getElementCount() != 2) throw new IllegalStateException("Unsupported UV: " + el);
						uvOff = fmt.getOffset(i);
					} else {
						if (el.getType().getSize() > 2 || el.getElementCount() != 2) throw new IllegalStateException("Unsupported LMAP: " + el);
						lmapOff = fmt.getOffset(i);
						lmapType = el.getType().getSize();
					}
					break;
				case COLOR:
					if (el.getType().getSize() != 1 || el.getElementCount() != 4) throw new IllegalStateException("Unsupported COLOR: " + el);
					colorOff = fmt.getOffset(i);
					break;
				case POSITION:
					if (el.getType() != VertexFormatElement.EnumType.FLOAT || el.getElementCount() != 3) throw new IllegalStateException("Unsupported POS: " + el);
					posOff = fmt.getOffset(i);
					break;
			}
		}

		if (posOff < 0) throw new IllegalStateException("No POS");

		format = fmt;
	}

	public final QuadMutator uv1(float u0, float v0, float u1, float v1) {
		int off = uvOff;
		if (off < 0) throw new IllegalStateException("No UV");
		int len = this.len;

		data.putFloat(off, u1)
			.putFloat(off + 4, v0)
			.putFloat(off + len, u0)
			.putFloat(off + len + 4, v0)
			.putFloat(off + len * 2, u0)
			.putFloat(off + len * 2 + 4, v1)
			.putFloat(off + len * 3, u1)
			.putFloat(off + len * 3 + 4, v1);
		return this;
	}

	public final QuadMutator pos1(float x0, float y0, float x1, float y1) {
		Mat4x3f mat = this.modelview;
		Vec4f tmp = this.off;
		int off = posOff;
		int len = this.len;
		ByteList list = this.data;

		mat.mul(tmp.set(x0, y1), tmp);
		list.putFloat(off, tmp.x).putFloat(off + 4, tmp.y).putFloat(off + 8, tmp.z);

		off += len;
		mat.mul(tmp.set(x1, y1), tmp);
		list.putFloat(off, tmp.x).putFloat(off + 4, tmp.y).putFloat(off + 8, tmp.z);

		off += len;
		mat.mul(tmp.set(x1, y0), tmp);
		list.putFloat(off, tmp.x).putFloat(off + 4, tmp.y).putFloat(off + 8, tmp.z);

		off += len;
		mat.mul(tmp.set(x0, y0), tmp);
		list.putFloat(off, tmp.x).putFloat(off + 4, tmp.y).putFloat(off + 8, tmp.z);
		return this;
	}

	public final QuadMutator lightmap1(int skyLight, int blockLight) {
		int off = lmapOff;
		if (off < 0) throw new IllegalStateException("No LMAP");
		int len = this.len;
		switch (lmapType) {
			case 2:
				data.putShort(off, skyLight)
					.putShort(off + 2, blockLight)
					.putShort(off + len, skyLight)
					.putShort(off + len + 2, blockLight)
					.putShort(off + len * 2, skyLight)
					.putShort(off + len * 2 + 2, blockLight)
					.putShort(off + len * 3, skyLight)
					.putShort(off + len * 3 + 2, blockLight);
				break;
			case 1:
				byte sl = (byte) skyLight;
				byte bl = (byte) blockLight;
				data.put(off, sl).put(off + 1, bl).put(off + len, sl).put(off + len + 1, bl).put(off + len * 2, sl).put(off + len * 2 + 1, bl).put(off + len * 3, sl).put(off + len * 3 + 1, bl);
				break;
		}
		return this;
	}

	public final QuadMutator color1(int rgba) {
		int off = colorOff;
		if (off < 0) throw new IllegalStateException("No COLOR");
		int len = this.len;
		data.putInt(off, rgba).putInt(off + len, rgba).putInt(off + len * 2, rgba).putInt(off + len * 3, rgba);
		return this;
	}

	public final QuadMutator normal1(float x, float y, float z) {
		normal.set(x, y, z);
		return this;
	}

	public BakedQuad bake() {
		if (triangle) quadulate();

		EnumFacing side;
		if (facing == null) {
			computeNormal();
			side = getSideByNormal(normal);
		} else {
			side = facing;
		}
		normal.x = Float.NaN;

		ByteList data = this.data;
		int[] arr = new int[data.wIndex() >> 2];
		for (int i = 0; i < arr.length; i++) {
			arr[i] = data.readInt(i << 2);
		}

		return new BakedQuad(arr, tintIndex, side, sprite, diffuseLighting, format);
	}

	private void quadulate() {
		System.arraycopy(data.list, len * 2, data.list, len * 3, len);
	}

	private void computeNormal() {
		if (normal.x == normal.x) return;

		ByteList list = this.data;
		int off = posOff;
		int len = this.len;

		// one.sub(zero).cross(three.sub(zero)).normalize();
		float v0x = list.readFloat(off);
		float v0y = list.readFloat(off + 4);
		float v0z = list.readFloat(off + 8);

		float v1x = list.readFloat(off + len) - v0x;
		float v1y = list.readFloat(off + len + 4) - v0y;
		float v1z = list.readFloat(off + len + 8) - v0z;

		float v3x = list.readFloat(off + len * 3) - v0x;
		float v3y = list.readFloat(off + len * 3 + 4) - v0y;
		float v3z = list.readFloat(off + len * 3 + 8) - v0z;

		float x = v1y * v3z - v3y * v1z;
		float y = v1z * v3x - v3z * v1x;
		float z = v1x * v3y - v3x * v1y;

		float abs = MathUtils.sqrt(x * x + y * y + z * z);
		normal.set(x / abs, y / abs, z / abs);
	}

	private static EnumFacing getSideByNormal(Vec3f normal) {
		if (normal.y <= -0.99) return EnumFacing.DOWN;
		if (normal.y >= 0.99) return EnumFacing.UP;
		if (normal.z <= -0.99) return EnumFacing.NORTH;
		if (normal.z >= 0.99) return EnumFacing.SOUTH;
		if (normal.x <= -0.99) return EnumFacing.WEST;
		if (normal.x >= 0.99) return EnumFacing.EAST;
		return null;
	}

	public static int roundUp(float val, int bound) {
		return Float.floatToRawIntBits(Math.round(val * bound) / (float) bound);
	}
}
