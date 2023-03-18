package roj.config.serial;

import roj.asm.type.IType;
import roj.collect.MyHashMap;
import roj.util.Helpers;

import java.util.List;
import java.util.Map;

/**
 * @author Roj233
 * @since 2022/1/11 17:45
 */
final class MapSer extends Adapter {
	final Adapter targetSer;

	MapSer(Adapter type) {
		this.targetSer = type;
	}

	@Override
	Adapter withGenericType(SerializerManager man, List<IType> genericType) {
		if (genericType.size() != 2) throw new IllegalArgumentException(genericType.toString());

		Adapter genSer = man.get(genericType.get(1));
		return genSer == null ? this : new MapSer(genSer);
	}

	@Override
	protected void map(AdaptContext ctx, int size) {
		ctx.fieldId = -1;
		ctx.ref = size < 0 ? new MyHashMap<>() : new MyHashMap<>(size);
	}

	@Override
	protected void key(AdaptContext ctx, String key) {
		if (ctx.serCtx != null) throw new IllegalStateException("has key set");
		ctx.serCtx = key;

		ctx.fieldState = 0;
		ctx.pushHook(0, targetSer);
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
			targetSer.write(c, entry.getValue());
		}
	}
}
