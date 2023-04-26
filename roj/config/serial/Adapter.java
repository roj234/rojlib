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

	void read(AdaptContext ctx, boolean l) {read(ctx, (Object)l);}
	void read(AdaptContext ctx, int l) {read(ctx, (Object)l);}
	void read(AdaptContext ctx, long l) {read(ctx, (Object)l);}
	void read(AdaptContext ctx, float l) {read(ctx, (double)l);}
	void read(AdaptContext ctx, double l) {read(ctx, (Object)l);}
	void read(AdaptContext ctx, Object o) {
		throw new IllegalArgumentException(ctx.curr+"未预料的类型:"+(o==null?null:TypeHelper.class2asm(o.getClass())));
	}
	boolean read(AdaptContext ctx, byte[] o) { return false; }
	boolean read(AdaptContext ctx, int[] o) { return false; }
	boolean read(AdaptContext ctx, long[] o) { return false; }

	void map(AdaptContext ctx, int size) {une(ctx);}
	void list(AdaptContext ctx, int size) {une(ctx);}
	void key(AdaptContext ctx, String key) {une(ctx);}
	void push(AdaptContext ctx) {}
	void pop(AdaptContext ctx) {}

	private static void une(AdaptContext ctx) { throw new IllegalArgumentException(ctx.curr+"未预料的结构:"+ctx.ref); }

	int fieldCount() { return 1; }
	int plusOptional(int fieldState, @Nullable MyBitSet fieldStateEx) { return fieldState; }
	boolean valueIsMap() { return getClass().getName().contains("GenSer"); }

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
