package roj.config.mapper;

import org.jetbrains.annotations.Nullable;
import roj.asm.type.IType;
import roj.collect.BitSet;
import roj.collect.HashMap;
import roj.config.ValueEmitter;

import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * @author Roj233
 * @since 2022/1/11 17:45
 */
final class MapAdapter extends TypeAdapter {
	private final TypeAdapter keyType, valueType;
	private final IntFunction<Map<String,?>> newMap;

	MapAdapter(TypeAdapter kType, TypeAdapter vType, IntFunction<Map<String,?>> newMap) {
		this.keyType = getAdapter(kType);
		this.valueType = vType;
		this.newMap = newMap;
	}
	@Nullable
	private static TypeAdapter getAdapter(TypeAdapter kType) {return kType == PrimitiveAdapter.STR ? null : kType;}

	@Override
	public TypeAdapter transform(Factory man, Class<?> subclass, List<IType> generic) {
		var kType = keyType;
		var vType = valueType;
		if (generic != null) {
			if (generic.size() != 2) throw new IllegalArgumentException(generic.toString());
			kType = man.get(generic.get(0));
			vType = man.get(generic.get(1));
		}

		IntFunction<Map<String, ?>> constructor = ObjectMapperFactory.containerFactory(subclass);
		if (constructor == null) constructor = newMap;

		return getAdapter(kType) == getAdapter(keyType) && vType == valueType && constructor == newMap ? this : new MapAdapter(kType, vType, constructor);
	}

	@Override
	public void map(MappingContext ctx, int size) {
		ctx.fieldId = -1;
		ctx.setRef(newMap != null ? newMap.apply(size) : size < 0 ? new HashMap<>() : new HashMap<>(size));
	}

	@Override
	public void key(MappingContext ctx, String key) {
		if (ctx.ref2 != null) throw new IllegalStateException("has key set");
		ctx.ref2 = key;

		ctx.fieldState = 0;
		ctx.pushHook(0, valueType);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void read(MappingContext ctx, Object o) {
		if (ctx.fieldState == 1) {ctx.ref2 = o;return;}

		if (ctx.ref2 == null) {
			assert o == null : "ConfigParser violates constraint";
			ctx.popd(true);
			return;
		}

		ctx.setFieldHook();
		var ref = (Map<Object,Object>) ctx.ref;

		if (keyType != null) {
			ctx.fieldState = 1;

			var name = ctx.ref2;

			ctx.push(keyType);
			ctx.emit(String.valueOf(name));
		}
		ref.put(ctx.ref2, o);
		ctx.ref2 = null;
	}

	@Override
	public boolean valueIsMap() { return true; }

	// empty map
	@Override
	public int plusOptional(int fieldState, @Nullable BitSet fieldStateEx) { return 1; }

	@Override
	public void write(ValueEmitter c, Object o) {
		Map<?,?> ref = (Map<?,?>) o;
		if (ref == null) c.emitNull();
		else {
			c.emitMap(ref.size());
			writeMap(c, ref);
			c.pop();
		}
	}

	@Override
	public void writeMap(ValueEmitter c, Object o) {
		if (valueType == null) throw new IllegalStateException("开启Dynamic模式以序列化任意对象");

		Map<?,?> ref = (Map<?,?>) o;
		for (Map.Entry<?,?> entry : ref.entrySet()) {
			// 能不能反序列化回来，就不是我考虑的事情了
			if (keyType == null || !(entry.getKey() instanceof Integer idx)) {
				c.emitKey(String.valueOf(entry.getKey()));
			} else {
				c.emitKey(idx);
			}
			valueType.write(c, entry.getValue());
		}
	}
}