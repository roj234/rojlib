package roj.dev.hr;

import roj.collect.Int2IntMap;

import java.lang.ref.PhantomReference;

import static roj.reflect.FieldAccessor.u;

/**
 * @author Roj234
 * @since 2023/9/24 0024 1:58
 */
public final class HRFieldStorage extends PhantomReference<Object> {
	HRFieldStorage prev, next;

	public final String type;
	public final boolean _static;
	private Object[] o_fields;
	private int[] p_fields;

	public static HRFieldStorage create(Object o) {
		Class<?> klass = o.getClass();
		HRContext ctx = (HRContext) klass.getClassLoader();
		return new HRFieldStorage(o, ctx);
	}
	public static HRFieldStorage create(Class<?> klass) {
		HRContext ctx = (HRContext) klass.getClassLoader();
		return new HRFieldStorage(klass, ctx);
	}

	private HRFieldStorage(Object o, HRContext ctx) {
		super(o, ctx.queue);
		_static = o instanceof Class;
		type = _static ? ((Class<?>) o).getName() : o.getClass().getName();
		ctx.addFieldRef(this);
		update(ctx.structure.get(type));
	}

	final void update(HRContext.Structure info) {
		Object[] prev_o = o_fields;
		int[] prev_p = p_fields;

		Int2IntMap map;
		if (_static) {
			o_fields = info.o_size_static == 0 ? null : new Object[info.o_size_static];
			p_fields = info.p_size_static == 0 ? null : new int[info.p_size_static];
			map = info.fieldsMovedStatic;
		} else {
			o_fields = info.o_size == 0 ? null : new Object[info.o_size];
			p_fields = info.p_size == 0 ? null : new int[info.p_size];
			map = info.fieldsMoved;
		}

		if (!map.isEmpty()) {
			for (Int2IntMap.Entry entry : map.selfEntrySet()) {
				int from = entry.getIntKey();
				if (from < 0) p_fields[entry.v] = prev_p[-from - 1];
				else o_fields[entry.v] = prev_o[from];
			}
		}
	}

	public final int getI(int i) { return p_fields[i]; }
	public final float getF(int i) { return u.getFloat(p_fields,(long) i); }
	public final double getD(int i) { return u.getDouble(p_fields,(long) i); }
	public final long getJ(int i) { return u.getLong(p_fields,(long) i); }
	public final Object getA(int i) { return o_fields[i]; }

	public final void setI(int i, int v) { p_fields[i] = v; }
	public final void setF(int i, float v) { u.putFloat(p_fields,(long) i,v); }
	public final void setD(int i, double v) { u.putDouble(p_fields,(long) i,v); }
	public final void setJ(int i, long v) { u.putLong(p_fields,(long) i,v); }
	public final void setA(int i, Object v) { o_fields[i] = v; }
}
