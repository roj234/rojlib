package roj.config.auto;

import org.jetbrains.annotations.Nullable;
import roj.ReferenceByGeneratedClass;
import roj.asm.type.IType;
import roj.asm.type.TypeHelper;
import roj.collect.MyBitSet;
import roj.config.serial.CVisitor;

import java.util.List;

/**
 * @author Roj234
 * @since 2023/3/18 0018 12:55
 */
abstract class Adapter {
	@ReferenceByGeneratedClass
	static void value(CVisitor v, String s) {if(s == null) v.valueNull(); else v.value(s);}

	public Adapter transform(SerializerFactoryImpl man, Class<?> subclass, @Nullable List<IType> generic) { return this; }

	public void read(AdaptContext ctx, boolean l) {read(ctx, (Object)l);}
	public void read(AdaptContext ctx, int l) {read(ctx, (Object)l);}
	public void read(AdaptContext ctx, long l) {read(ctx, (Object)l);}
	public void read(AdaptContext ctx, float l) {read(ctx, (double)l);}
	public void read(AdaptContext ctx, double l) {read(ctx, (Object)l);}
	public void read(AdaptContext ctx, Object o) {
		throw new IllegalArgumentException(this+"不支持的类型:"+(o==null?null:TypeHelper.class2asm(o.getClass()))+"【"+ctx.ref+"】");
	}
	public void read(AdaptContext ctx, byte[] o) {
		list(ctx,o.length);
		for (byte b : o) read(ctx,b);
		ctx.pop();
	}
	public void read(AdaptContext ctx, int[] o) {
		list(ctx,o.length);
		for (int b : o) read(ctx,b);
		ctx.pop();
	}
	public void read(AdaptContext ctx, long[] o) {
		list(ctx,o.length);
		for (long b : o) read(ctx,b);
		ctx.pop();
	}

	public void map(AdaptContext ctx, int size) {une(ctx,"mapping["+size+"]");}
	public void list(AdaptContext ctx, int size) {une(ctx,"array["+size+"]");}
	public void key(AdaptContext ctx, String key) {une(ctx,"key["+key+"]");}
	public void push(AdaptContext ctx) {}
	public void pop(AdaptContext ctx) {}

	private void une(AdaptContext ctx, String msg) { throw new IllegalArgumentException(this+"不支持的结构:"+msg+"【"+ctx.ref+"】"); }

	public int fieldCount() { return 1; }
	public int plusOptional(int fieldState, @Nullable MyBitSet fieldStateEx) { return fieldState; }
	public boolean valueIsMap() { return getClass().getName().contains("GA$"); }

	public void write(CVisitor c, Object o) {
		if (o == null) c.valueNull();
		else {
			c.valueMap(fieldCount());
			writeMap(c, o);
			c.pop();
		}
	}
	public void writeMap(CVisitor c, Object o) { throw new UnsupportedOperationException(this+" should override write() or writeMap()"); }
}