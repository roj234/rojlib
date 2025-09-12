package roj.config.mapper;

import roj.collect.BitSet;
import roj.collect.ToIntMap;
import roj.config.ValueEmitter;

/**
 * @author Roj234
 * @since 2024/3/26 0:09
 */
final class PooledAdapter extends TypeAdapter {
	static final TypeAdapter PrevObjectId = new TypeAdapter() {
		@Override
		public void read(MappingContext ctx, int no) {
			if (!(ctx instanceof MappingContextEx x) || no < 0 || no > x.objectsR.size()) {
				throw new IllegalArgumentException("PrevObjectId("+no+")");
			}
			ctx.ref = x.objectsR.get(no);
			ctx.fieldState = 1;
		}
	};

	private final TypeAdapter adapter;
	PooledAdapter(TypeAdapter adapter) {this.adapter = adapter;}

	public void read(MappingContext ctx, boolean l) {adapter.read(ctx, l);}
	public void read(MappingContext ctx, int l) {adapter.read(ctx, l);}
	public void read(MappingContext ctx, long l) {adapter.read(ctx, l);}
	public void read(MappingContext ctx, float l) {adapter.read(ctx, l);}
	public void read(MappingContext ctx, double l) {adapter.read(ctx, l);}
	public void read(MappingContext ctx, Object o) {adapter.read(ctx, o);}
	public void read(MappingContext ctx, byte[] o) {adapter.read(ctx, o);}
	public void read(MappingContext ctx, int[] o) {adapter.read(ctx, o);}
	public void read(MappingContext ctx, long[] o) {adapter.read(ctx, o);}

	public void map(MappingContext ctx, int size) {((MappingContextEx) ctx).captureRef();adapter.map(ctx,size);}
	public void list(MappingContext ctx, int size) {adapter.list(ctx,size);}
	public void key(MappingContext ctx, String key) {
		if (key.isEmpty()) {
			((MappingContextEx) ctx).objectsR.pop();
			ctx.replace(PrevObjectId);
		} else {
			adapter.key(ctx,key);
		}
	}
	public void push(MappingContext ctx) {adapter.push(ctx);}
	public void pop(MappingContext ctx) {adapter.pop(ctx);}

	public int fieldCount() {return adapter.fieldCount();}
	public int plusOptional(int fieldState, BitSet fieldStateEx) {return adapter.plusOptional(fieldState, fieldStateEx);}
	public boolean valueIsMap() {return adapter.valueIsMap();}

	public void write(ValueEmitter c, Object o) {
		if (valueIsMap() && o != null) {
			ToIntMap<Object> pool = MappingContextEx.OBJECT_POOL.get();
			assert pool != null;

			int objectId = pool.putOrGet(o, pool.size(), -1);
			if (objectId >= 0) {
				c.emitMap(1);
				c.key("");
				c.emit(objectId);
				c.pop();
				return;
			}
		}
		adapter.write(c, o);
	}
	public void writeMap(ValueEmitter c, Object o) {adapter.writeMap(c,o);}
}