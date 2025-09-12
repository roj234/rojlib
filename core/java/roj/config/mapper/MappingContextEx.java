package roj.config.mapper;

import roj.collect.ArrayList;
import roj.collect.Hasher;
import roj.collect.ToIntMap;
import roj.concurrent.LazyThreadLocal;
import roj.config.ValueEmitter;

/**
 * @author Roj234
 * @since 2023/3/19 19:16
 */
final class MappingContextEx extends MappingContext {
	ArrayList<Object> objectsR = new ArrayList<>();
	ToIntMap<Object> objectW = new ToIntMap<>();

	public MappingContextEx(TypeAdapter root, boolean allowDeser) { super(root, allowDeser); objectW.setHasher(Hasher.identity()); }

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
	public final ObjectMapper<Object> reset() {objectsR.clear();return super.reset();}

	static final LazyThreadLocal<ToIntMap<Object>> OBJECT_POOL = new LazyThreadLocal<>();
	public final void write(ValueEmitter emitter, Object value) {
		ToIntMap<Object> prev = OBJECT_POOL.get();
		OBJECT_POOL.set(objectW);
		try {
			super.write(emitter, value);
		} finally {
			if (prev != null) OBJECT_POOL.set(prev);
			else OBJECT_POOL.remove();

			objectW.clear();
		}
	}
}