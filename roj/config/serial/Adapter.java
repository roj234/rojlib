package roj.config.serial;

import org.jetbrains.annotations.Nullable;
import roj.asm.type.IType;
import roj.asm.type.TypeHelper;
import roj.collect.MyBitSet;

import java.util.List;

/**
 * @author Roj234
 * @since 2023/3/18 0018 12:55
 */
abstract class Adapter {
	Adapter withGenericType(SerializerFactory man, List<IType> genericType) { return this; }
	public Adapter inheritBy(SerializerFactory factory, Class<?> type) {
		return this;
	}

	void read(AdaptContext ctx, boolean l) {read(ctx, (Object)l);}
	void read(AdaptContext ctx, int l) {read(ctx, (Object)l);}
	void read(AdaptContext ctx, long l) {read(ctx, (Object)l);}
	void read(AdaptContext ctx, float l) {read(ctx, (double)l);}
	void read(AdaptContext ctx, double l) {read(ctx, (Object)l);}
	void read(AdaptContext ctx, Object o) {
		throw new IllegalArgumentException(ctx.curr+"不支持的类型:"+(o==null?null:TypeHelper.class2asm(o.getClass()))+"【"+ctx.ref+"】");
	}
	void read(AdaptContext ctx, byte[] o) {
		list(ctx,o.length);
		for (byte b : o) read(ctx,b);
		ctx.pop();
	}
	void read(AdaptContext ctx, int[] o) {
		list(ctx,o.length);
		for (int b : o) read(ctx,b);
		ctx.pop();
	}
	void read(AdaptContext ctx, long[] o) {
		list(ctx,o.length);
		for (long b : o) read(ctx,b);
		ctx.pop();
	}

	void map(AdaptContext ctx, int size) {une(ctx,"mapping["+size+"]");}
	void list(AdaptContext ctx, int size) {une(ctx,"array["+size+"]");}
	void key(AdaptContext ctx, String key) {une(ctx,"key["+key+"]");}
	void push(AdaptContext ctx) {}
	void pop(AdaptContext ctx) {}

	private static void une(AdaptContext ctx, String msg) { throw new IllegalArgumentException(ctx.curr+"不支持的结构:"+msg+"【"+ctx.ref+"】"); }

	int fieldCount() { return 1; }
	int plusOptional(int fieldState, @Nullable MyBitSet fieldStateEx) { return fieldState; }
	boolean valueIsMap() { return getClass().getName().contains("GA$"); }

	void write(CVisitor c, Object o) {
		if (o == null) c.valueNull();
		else {
			c.valueMap(fieldCount());
			writeMap(c, o);
			c.pop();
		}
	}
	void writeMap(CVisitor c, Object o) { throw new UnsupportedOperationException(this+" should override write() or writeMap()"); }
}
