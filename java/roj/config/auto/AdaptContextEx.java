package roj.config.auto;

import roj.collect.Hasher;
import roj.collect.ArrayList;
import roj.collect.ToIntMap;
import roj.concurrent.LazyThreadLocal;
import roj.config.serial.CVisitor;

/**
 * @author Roj234
 * @since 2023/3/19 19:16
 */
final class AdaptContextEx extends AdaptContext {
	ArrayList<Object> objectsR = new ArrayList<>();
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

	static final LazyThreadLocal<ToIntMap<Object>> OBJECT_POOL = new LazyThreadLocal<>();
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