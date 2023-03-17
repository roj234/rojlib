package ilib.anim;

import roj.collect.SimpleList;
import roj.math.Mat4x3f;
import roj.util.ByteList;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/5/27 22:00
 */
public final class Keyframes {
	private final String name;
	private final SimpleList<Keyframe> keyframes = new SimpleList<>();
	private final Mat4x3f mat = new Mat4x3f();

	public Keyframes(String name) {
		this.name = name;
	}

	private int binarySearch(int key) {
		List<Keyframe> a = keyframes;

		int low = 0;
		int high = a.size() - 1;

		while (low <= high) {
			int mid = (low + high) >>> 1;
			int midVal = a.get(mid).time;

			if (midVal < key) {
				low = mid + 1;
			} else if (midVal > key) {
				high = mid - 1;
			} else {return mid;}
		}

		return -(low + 1);
	}

	public List<Keyframe> internal() {
		return keyframes;
	}

	public int size() {
		return keyframes.size();
	}

	public int duration() {
		return keyframes.get(keyframes.size() - 1).time;
	}

	public Mat4x3f interpolate(double time) {
		SimpleList<Keyframe> kfs = keyframes;

		if (kfs.isEmpty()) return mat.makeIdentity();

		int i = binarySearch((int) Math.ceil(time));
		if (i < 0) i = -i - 1;

		if (i == 0) return kfs.get(0);
		if (i > kfs.size() - 1) return kfs.get(kfs.size() - 1);

		Keyframe curr = kfs.get(i - 1), next = kfs.get(i);

		return Mat4x3f.mix(next, curr, (float) ((time - curr.time) / (next.time - curr.time)), mat);
	}

	public int add(Keyframe kf) {
		int i = binarySearch(kf.time);
		if (i < 0) {keyframes.add(i = -i - 1, kf);} else keyframes.set(i, kf);
		return i;
	}

	public Keyframe remove(int index) {
		return keyframes.remove(index);
	}

	public int applyTransform(int tick, byte type, float x, float y, float z) {
		int i = binarySearch(tick);
		if (i < 0) {
			i = -i - 1;
			Keyframe kf = new Keyframe();
			kf.time = tick;
			kf.set(type, x, y, z);
			keyframes.add(i, kf);
		} else {keyframes.get(i).set(type, x, y, z);}

		return i;
	}

	public String getName() {
		return name;
	}

	@Override
	public String toString() {
		return "Keyframes{" + "name='" + name + '\'' + '}';
	}

	public void copyFrom(Keyframes other) {
		keyframes.clear();

		List<Keyframe> otherKf = other.keyframes;
		for (int i = 0; i < otherKf.size(); i++) {
			keyframes.add(new Keyframe(otherKf.get(i)));
		}
	}

	public static Keyframes fromByteArray(ByteList r) {
		Keyframes kfs = new Keyframes(r.readVIVIC());
		for (int len = r.readVarInt(false); len > 0; len--) {
			kfs.add(Keyframe.fromByteArray(r));
		}

		return kfs;
	}

	public void toByteArray(ByteList w) {
		w.putVarIntVIC(name).putVarInt(keyframes.size(), false);

		List<Keyframe> kfs = keyframes;
		for (int i = 0; i < kfs.size(); i++) {
			kfs.get(i).toByteArray(w);
		}
	}
}
