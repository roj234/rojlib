package roj.config.auto;

import roj.collect.MyBitSet;
import roj.collect.ToIntMap;
import roj.config.serial.CVisitor;

/**
 * @author Roj234
 * @since 2024/3/26 0026 0:09
 */
final class ObjectPoolWrapper extends Adapter {
	static final Adapter PrevObjectId = new Adapter() {
		@Override
		void read(AdaptContext ctx, int no) {
			if (!(ctx instanceof AdaptContextEx x) || no < 0 || no > x.objectsR.size()) {
				throw new IllegalArgumentException("PrevObjectId("+no+")");
			}
			ctx.ref = x.objectsR.get(no);
			ctx.fieldState = 1;
		}
	};

	private final Adapter ada;
	ObjectPoolWrapper(Adapter ada) {this.ada = ada;}

	void read(AdaptContext ctx, boolean l) {ada.read(ctx, l);}
	void read(AdaptContext ctx, int l) {ada.read(ctx, l);}
	void read(AdaptContext ctx, long l) {ada.read(ctx, l);}
	void read(AdaptContext ctx, float l) {ada.read(ctx, l);}
	void read(AdaptContext ctx, double l) {ada.read(ctx, l);}
	void read(AdaptContext ctx, Object o) {ada.read(ctx, o);}
	void read(AdaptContext ctx, byte[] o) {ada.read(ctx, o);}
	void read(AdaptContext ctx, int[] o) {ada.read(ctx, o);}
	void read(AdaptContext ctx, long[] o) {ada.read(ctx, o);}

	void map(AdaptContext ctx, int size) {((AdaptContextEx) ctx).captureRef();ada.map(ctx,size);}
	void list(AdaptContext ctx, int size) {ada.list(ctx,size);}
	void key(AdaptContext ctx, String key) {
		if (key.equals("==")) {
			((AdaptContextEx) ctx).objectsR.pop();
			ctx.replace(PrevObjectId);
		} else {
			ada.key(ctx,key);
		}
	}
	void push(AdaptContext ctx) {ada.push(ctx);}
	void pop(AdaptContext ctx) {ada.pop(ctx);}

	int fieldCount() {return ada.fieldCount();}
	int plusOptional(int fieldState, MyBitSet fieldStateEx) {return ada.plusOptional(fieldState, fieldStateEx);}
	boolean valueIsMap() {return ada.valueIsMap();}

	void write(CVisitor c, Object o) {
		if (valueIsMap() && o != null) {
			ToIntMap<Object> pool = AdaptContextEx.OBJECT_POOL.get();
			assert pool != null;

			int objectId = pool.putOrGet(o, pool.size(), -1);
			if (objectId >= 0) {
				c.valueMap(1);
				c.key("==");
				c.value(objectId);
				c.pop();
				return;
			}
		}
		ada.write(c, o);
	}
	void writeMap(CVisitor c, Object o) {ada.writeMap(c,o);}
}