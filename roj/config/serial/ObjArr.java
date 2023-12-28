package roj.config.serial;

import roj.collect.SimpleList;

import java.lang.reflect.Array;

/**
 * @author Roj234
 * @since 2023/3/19 0019 20:31
 */
final class ObjArr extends Adapter {
	private final Class<?> type;
	private final Adapter ser;

	ObjArr(Class<?> type, Adapter ser) {
		this.type = type;
		this.ser = ser;
	}

	@Override
	void pop(AdaptContext ctx) {
		if (ctx.fieldId == -1) {
			SimpleList<?> buf = (SimpleList<?>) ctx.ref;
			ctx.ref = buf.toArray((Object[]) Array.newInstance(type,buf.size()));
			ctx.releaseBuffer(buf);
		}
	}

	@Override
	void list(AdaptContext ctx, int size) {
		if (size < 0) {
			ctx.ref = ctx.objBuffer();
			ctx.fieldId = -1;
		} else {
			ctx.ref = Array.newInstance(type,size);
			ctx.fieldId = 0;
		}

		ctx.push(ser);
	}

	@Override
	@SuppressWarnings("unchecked")
	void read(AdaptContext ctx, Object o) {
		if (ctx.fieldId == -1) {
			((SimpleList<Object>) ctx.ref).add(o);
		} else {
			((Object[]) ctx.ref)[ctx.fieldId++] = o;
		}

		ctx.fieldState = 1;
		ctx.push(ser);
	}

	@Override
	void write(CVisitor c, Object o) {
		if (o == null) c.valueNull();
		else {
			Object[] arr = ((Object[]) o);
			c.valueList(arr.length);
			for (Object o1 : arr) ser.write(c, o1);
			c.pop();
		}
	}
}