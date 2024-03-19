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

	final void addRef() {objectsR.add(ref);}
	public final Serializer<Object> reset() {objectsR.clear();return super.reset();}

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