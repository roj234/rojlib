package roj.config.mapper;

import roj.collect.ToIntMap;
import roj.concurrent.LazyThreadLocal;
import roj.config.ValueEmitter;

/**
 * @author Roj234
 * @since 2025/9/22 18:54
 */
final class ReentrantObjectWriter implements ObjectWriter<Object> {
	private final TypeAdapter root;
	public ReentrantObjectWriter(TypeAdapter root) {this.root = root;}

	static final LazyThreadLocal<ToIntMap<Object>> OBJECT_POOL = new LazyThreadLocal<>();
	public final void write(ValueEmitter emitter, Object value) {
		ToIntMap<Object> prev = OBJECT_POOL.get();
		OBJECT_POOL.set(new ToIntMap<>());
		try {
			root.write(emitter, value);
		} finally {
			if (prev != null) OBJECT_POOL.set(prev);
			else OBJECT_POOL.remove();
		}
	}
}