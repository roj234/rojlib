package roj.config.auto;

import roj.collect.Hasher;
import roj.collect.SimpleList;
import roj.collect.ToIntMap;
import roj.config.serial.CVisitor;

/**
 * @author Roj234
 * @since 2023/3/19 0019 19:16
 */
final class AdaptContextEx extends AdaptContext {
	SimpleList<Object> objectsR = new SimpleList<>();
	ToIntMap<Object> objectW = new ToIntMap<>();

	public AdaptContextEx(Adapter root) { super(root); objectW.setHasher(Hasher.identity()); }

	private boolean capture;
	final void captureRef() {
		if (capture) throw new IllegalStateException();
		capture = true;
	}

	@Override
	void setRef(Object o) {
		ref = o;
		if (capture) {
			capture = false;
			objectsR.add(o);
		}
	}
	public final Serializer<Object> reset() {objectsR.clear();return super.reset();}

	static final ThreadLocal<ToIntMap<Object>> OBJECT_POOL = new ThreadLocal<>();
	public final void write(CVisitor c, Object o) {
		ToIntMap<Object> prev = OBJECT_POOL.get();
		OBJECT_POOL.set(objectW);
		try {
			super.write(c, o);
		} finally {
			if (prev != null) OBJECT_POOL.set(prev);
			else OBJECT_POOL.remove();

			objectW.clear();
		}
	}
}