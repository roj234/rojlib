package roj.config.auto;

import org.jetbrains.annotations.Nullable;
import roj.asm.type.IType;
import roj.collect.MyBitSet;
import roj.collect.MyHashMap;
import roj.config.serial.CVisitor;

import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * @author Roj233
 * @since 2022/1/11 17:45
 */
final class MapSer extends Adapter {
	private final Adapter keyType, valueType;
	private final IntFunction<Map<String,?>> newMap;

	MapSer(Adapter kType, Adapter vType, IntFunction<Map<String,?>> newMap) {
		this.keyType = getAdapter(kType);
		this.valueType = vType;
		this.newMap = newMap;
	}
	@Nullable
	private static Adapter getAdapter(Adapter kType) {return kType == PrimObj.STR ? null : kType;}

	@Override
	public Adapter transform(SerializerFactoryImpl man, Class<?> subclass, List<IType> generic) {
		var kType = keyType;
		var vType = valueType;
		if (generic != null) {
			if (generic.size() != 2) throw new IllegalArgumentException(generic.toString());
			kType = man.get(generic.get(0));
			vType = man.get(generic.get(1));
		}

		IntFunction<Map<String, ?>> constructor = SerializerFactory.dataContainer(subclass);
		if (constructor == null) constructor = newMap;

		return getAdapter(kType) == getAdapter(keyType) && vType == valueType && constructor == newMap ? this : new MapSer(kType, vType, constructor);
	}

	@Override
	public void map(AdaptContext ctx, int size) {
		ctx.fieldId = -1;
		ctx.setRef(newMap != null ? newMap.apply(size) : size < 0 ? new MyHashMap<>() : new MyHashMap<>(size));
	}

	@Override
	public void key(AdaptContext ctx, String key) {
		if (ctx.ref2 != null) throw new IllegalStateException("has key set");
		ctx.ref2 = key;

		ctx.fieldState = 0;
		ctx.pushHook(0, valueType);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void read(AdaptContext ctx, Object o) {
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
			ctx.value(String.valueOf(name));
		}
		ref.put(ctx.ref2, o);
		ctx.ref2 = null;
	}

	@Override
	public boolean valueIsMap() { return true; }

	// empty map
	@Override
	public int plusOptional(int fieldState, @Nullable MyBitSet fieldStateEx) { return 1; }

	@Override
	public void write(CVisitor c, Object o) {
		Map<?,?> ref = (Map<?,?>) o;
		if (ref == null) c.valueNull();
		else {
			c.valueMap(ref.size());
			writeMap(c, ref);
			c.pop();
		}
	}

	@Override
	public void writeMap(CVisitor c, Object o) {
		if (valueType == null) throw new IllegalStateException("开启Dynamic模式以序列化任意对象");

		Map<?,?> ref = (Map<?,?>) o;
		for (Map.Entry<?,?> entry : ref.entrySet()) {
			// 能不能反序列化回来，就不是我考虑的事情了
			if (keyType == null) {
				c.key(String.valueOf(entry.getKey()));
			} else {
				var c1 = new ToStringSerializer();
				keyType.write(c1, entry.getKey());
				c.key(c1.value);
			}
			valueType.write(c, entry.getValue());
		}
	}
	private static class ToStringSerializer implements CVisitor {
		String value;
		@Override public void value(boolean l) {throw new IllegalArgumentException("String excepted");}
		@Override public void value(int l) {throw new IllegalArgumentException("String excepted");}
		@Override public void value(long l) {throw new IllegalArgumentException("String excepted");}
		@Override public void value(double l) {throw new IllegalArgumentException("String excepted");}
		@Override public void value(String l) {this.value = l;}
		@Override public void valueNull() {value("null");}
		@Override public void valueMap() {throw new IllegalArgumentException("String excepted");}
		@Override public void key(String key) {}
		@Override public void valueList() {throw new IllegalArgumentException("String excepted");}
		@Override public void pop() {}
		@Override public CVisitor reset() {return this;}
	}
}