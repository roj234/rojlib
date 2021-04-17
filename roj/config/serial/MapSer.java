package roj.config.serial;

import org.jetbrains.annotations.Nullable;
import roj.asm.type.IType;
import roj.collect.MyBitSet;
import roj.collect.MyHashMap;
import roj.util.Helpers;

import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * @author Roj233
 * @since 2022/1/11 17:45
 */
final class MapSer extends Adapter {
	private final Adapter valueType;
	private final IntFunction<Map<String,?>> newMap;

	MapSer(Adapter type, IntFunction<Map<String,?>> newMap) { this.valueType = type; this.newMap = newMap; }

	@Override
	Adapter withGenericType(SerializerFactory man, List<IType> genericType) {
		if (genericType.size() != 2) throw new IllegalArgumentException(genericType.toString());

		Adapter value = man.get(genericType.get(1));
		return value == null ? this : new MapSer(value, newMap);
	}

	@Override
	protected void map(AdaptContext ctx, int size) {
		ctx.fieldId = -1;
		ctx.ref = newMap != null ? newMap.apply(size) : size < 0 ? new MyHashMap<>() : new MyHashMap<>(size);
	}

	@Override
	protected void key(AdaptContext ctx, String key) {
		if (ctx.serCtx != null) throw new IllegalStateException("has key set");
		ctx.serCtx = key;

		ctx.fieldState = 0;
		ctx.pushHook(0, valueType);
	}

	@Override
	@SuppressWarnings("unchecked")
	protected void read(AdaptContext ctx, Object o) {
		if (ctx.serCtx == null) throw new IllegalStateException("no key set");

		ctx.setFieldHook();
		Map<String,?> ref = (Map<String,?>) ctx.ref;
		ref.put(ctx.serCtx, Helpers.cast(o));
		ctx.serCtx = null;
	}

	@Override
	boolean valueIsMap() { return true; }

	// empty map
	@Override
	int plusOptional(int fieldState, @Nullable MyBitSet fieldStateEx) { return 1; }

	@Override
	void write(CVisitor c, Object o) {
		Map<?,?> ref = (Map<?,?>) o;
		if (ref == null) c.valueNull();
		else {
			c.valueMap(ref.size());
			writeMap(c, ref);
			c.pop();
		}
	}

	@Override
	void writeMap(CVisitor c, Object o) {
		Map<?,?> ref = (Map<?,?>) o;
		for (Map.Entry<?,?> entry : ref.entrySet()) {
			try {
				c.key((String) entry.getKey());
			} catch (ClassCastException e) {
				throw new IllegalArgumentException("MapSer的map必须用字符串key");
			}
			valueType.write(c, entry.getValue());
		}
	}
}
