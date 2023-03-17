package ilib.anim;

import roj.math.Mat4x3f;
import roj.math.MathUtils;
import roj.util.ByteList;

/**
 * @author Roj234
 * @since 2021/5/27 21:58
 */
public final class Keyframe extends Mat4x3f {
	public int time, color;

	public Keyframe() {
		m00 = m11 = m22 = 1;
	}

	public Keyframe(int time) {
		this();
		this.time = time;
	}

	public Keyframe(Keyframe frame) {
		set(frame);
		time = frame.time;
		color = frame.color;
	}

	public void set(byte type, float x, float y, float z) {
		float tx = m03, ty = m13, tz = m23;
		float rx = (float) Math.asin(m21), ry = (float) Math.asin(m02), rz = (float) Math.asin(m10);
		float cx = MathUtils.cos(rx), cy = MathUtils.cos(ry), cz = MathUtils.cos(rz);
		float sx = m00 / (cy * cz), sy = m11 / (cx * cz), sz = m22 / (cx * cy);

		switch (type) {
			case 0:
				tx = x;
				ty = y;
				tz = z;
				break;
			case 1:
				rx = x;
				ry = y;
				rz = z;
				break;
			case 2:
				if (x == 0 || y == 0 || z == 0) {
					throw new IllegalArgumentException("scale(0)");
				}

				sx = x;
				sy = y;
				sz = z;
				break;
		}

		m00 = m11 = m22 = 1;
		m01 = m02 = m10 = m12 = m20 = m21 = 0;

		scale(sx, sy, sz).rotateX(rx).rotateY(ry).rotateZ(rz).translateAbs(tx, ty, tz);
	}

	public void toByteArray(ByteList w) {
		w.putVarInt(time, false)
		 .putInt(color)
		 .putFloat(m00)
		 .putFloat(m01)
		 .putFloat(m02)
		 .putFloat(m03)
		 .putFloat(m10)
		 .putFloat(m11)
		 .putFloat(m12)
		 .putFloat(m13)
		 .putFloat(m20)
		 .putFloat(m21)
		 .putFloat(m22)
		 .putFloat(m23);
	}

	public static Keyframe fromByteArray(ByteList r) {
		Keyframe kf = new Keyframe();
		kf.time = r.readVarInt(false);
		kf.color = r.readInt();
		kf.m00 = r.readFloat();
		kf.m01 = r.readFloat();
		kf.m02 = r.readFloat();
		kf.m03 = r.readFloat();
		kf.m10 = r.readFloat();
		kf.m11 = r.readFloat();
		kf.m12 = r.readFloat();
		kf.m13 = r.readFloat();
		kf.m20 = r.readFloat();
		kf.m21 = r.readFloat();
		kf.m22 = r.readFloat();
		kf.m23 = r.readFloat();
		return kf;
	}
}
