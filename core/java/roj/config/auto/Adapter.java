package roj.config.auto;

import org.jetbrains.annotations.Nullable;
import roj.asm.type.IType;
import roj.ci.annotation.Public;
import roj.ci.annotation.ReferenceByGeneratedClass;
import roj.collect.BitSet;
import roj.config.serial.CVisitor;

import java.util.List;

/**
 * @author Roj234
 * @since 2023/3/18 12:55
 */
@Public
/*abstract */class Adapter {
	static final Adapter NO_DESERIALIZE = new Adapter();

	@ReferenceByGeneratedClass
	static void value(CVisitor v, String s) {if(s == null) v.valueNull(); else v.value(s);}

	public Adapter transform(SerializerFactoryImpl man, Class<?> subclass, @Nullable List<IType> generic) { return this; }

	public void read(AdaptContext ctx, boolean l) {read(ctx, (Object)l);}
	public void read(AdaptContext ctx, int l) {read(ctx, (Object)l);}
	public void read(AdaptContext ctx, long l) {read(ctx, (Object)l);}
	public void read(AdaptContext ctx, float l) {read(ctx, (double)l);}
	public void read(AdaptContext ctx, double l) {read(ctx, (Object)l);}
	public void read(AdaptContext ctx, Object o) {ctx.ofIllegalType(this);}
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
	public void key(AdaptContext ctx, int key) {key(ctx,Integer.toString(key));}
	public void key(AdaptContext ctx, String key) {une(ctx,"key["+key+"]");}
	public void push(AdaptContext ctx) {}
	public void pop(AdaptContext ctx) {}

	private void une(AdaptContext ctx, String msg) { throw new IllegalArgumentException(this+"不支持的结构:"+msg+"【"+ctx.ref+"】"); }

	public int fieldCount() { return 1; }
	// TODO 20250531 如果未来哪天决定删除ObjectPool机制，可以把很多方法一起扬了，比如这个
	public boolean isOptional() { return false; }
	public int plusOptional(int fieldState, @Nullable BitSet fieldStateEx) { return fieldState; }
	public boolean valueIsMap() { return getClass().getName().contains("GA$"); }

	//WIP
	@ReferenceByGeneratedClass
	public boolean isEmpty(Object o) {return o != null;}
	//end WIP

	public void write(CVisitor c, Object o) {
		if (o == null) c.valueNull();
		else {
			if (isOptional()) c.valueMap();
			else c.valueMap(fieldCount());
			writeMap(c, o);
			c.pop();
		}
	}
	public void writeMap(CVisitor c, Object o) { throw new UnsupportedOperationException(this+" should override write() or writeMap()"); }
}