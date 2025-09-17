package roj.config.mapper;

import org.jetbrains.annotations.Nullable;
import roj.asm.type.IType;
import roj.ci.annotation.IndirectReference;
import roj.ci.annotation.Public;
import roj.collect.BitSet;
import roj.config.ValueEmitter;

import java.util.List;

/**
 * @author Roj234
 * @since 2023/3/18 12:55
 */
@Public
/*abstract */class TypeAdapter {
	static final TypeAdapter NO_DESERIALIZE = new TypeAdapter();

	@IndirectReference
	static void emit(ValueEmitter v, String s) {if(s == null) v.emitNull(); else v.emit(s);}

	public TypeAdapter transform(Factory man, Class<?> subclass, @Nullable List<IType> generic) { return this; }

	public void read(MappingContext ctx, boolean l) {read(ctx, (Object)l);}
	public void read(MappingContext ctx, int l) {read(ctx, (Object)l);}
	public void read(MappingContext ctx, long l) {read(ctx, (Object)l);}
	public void read(MappingContext ctx, float l) {read(ctx, (double)l);}
	public void read(MappingContext ctx, double l) {read(ctx, (Object)l);}
	public void read(MappingContext ctx, Object o) {ctx.ofIllegalType(this);}
	public void read(MappingContext ctx, byte[] o) {
		list(ctx,o.length);
		for (byte b : o) read(ctx,b);
		ctx.pop();
	}
	public void read(MappingContext ctx, int[] o) {
		list(ctx,o.length);
		for (int b : o) read(ctx,b);
		ctx.pop();
	}
	public void read(MappingContext ctx, long[] o) {
		list(ctx,o.length);
		for (long b : o) read(ctx,b);
		ctx.pop();
	}

	public void map(MappingContext ctx, int size) {une(ctx,"mapping["+size+"]");}
	public void list(MappingContext ctx, int size) {une(ctx,"array["+size+"]");}
	public void key(MappingContext ctx, int key) {key(ctx,Integer.toString(key));}
	public void key(MappingContext ctx, String key) {une(ctx,"key["+key+"]");}
	public void push(MappingContext ctx) {}
	public void pop(MappingContext ctx) {}

	private void une(MappingContext ctx, String msg) { throw new IllegalArgumentException(this+"不支持的结构:"+msg+"【"+ctx.ref+"】"); }

	public int fieldCount() { return 1; }
	// TODO 20250531 如果未来哪天决定删除ObjectPool机制，可以把很多方法一起扬了，比如这个
	public boolean isOptional() { return false; }
	public int plusOptional(int fieldState, @Nullable BitSet fieldStateEx) { return fieldState; }
	public boolean valueIsMap() { return getClass().getName().contains("GA$"); }

	public void write(ValueEmitter c, Object o) {
		if (o == null) c.emitNull();
		else {
			if (isOptional()) c.emitMap();
			else c.emitMap(fieldCount());
			writeMap(c, o);
			c.pop();
		}
	}
	public void writeMap(ValueEmitter c, Object o) { throw new UnsupportedOperationException(this+" should override write() or writeMap()"); }
}