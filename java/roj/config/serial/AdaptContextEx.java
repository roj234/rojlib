package roj.config.serial;

import roj.collect.SimpleList;
import roj.collect.ToIntMap;

import java.util.List;

/**
 * @author Roj234
 * @since 2023/3/19 0019 19:16
 */
final class AdaptContextEx extends AdaptContext {
	List<Object> objectsR = new SimpleList<>();
	ToIntMap<Object> objectW = new ToIntMap<>();

	public AdaptContextEx(Adapter root) { super(root); }

	public final void reset() {
		super.reset();
		objectsR.clear();
	}

	static final ThreadLocal<AdaptContextEx> LOCAL_OBJS = new ThreadLocal<>();
	public final void write(CVisitor c, Object o) {
		AdaptContextEx prev = LOCAL_OBJS.get();
		LOCAL_OBJS.set(this);
		try {
			super.write(c, o);
		} finally {
			if (prev != null) LOCAL_OBJS.set(prev);
			else LOCAL_OBJS.remove();

			objectW.clear();
		}
	}
}
