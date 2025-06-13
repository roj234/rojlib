package roj.config.auto;

import roj.collect.ArrayList;
import roj.config.serial.CVisitor;

import java.lang.reflect.Array;

/**
 * @author Roj234
 * @since 2023/3/19 20:31
 */
final class ObjArr extends Adapter {
	private final Class<?> type;
	private final Adapter ser;

	ObjArr(Class<?> type, Adapter ser) {
		this.type = type;
		this.ser = ser;
	}

	@Override
	public void pop(AdaptContext ctx) {
		if (ctx.fieldId == -1) {
			ArrayList<?> buf = (ArrayList<?>) ctx.ref;
			ctx.setRef(buf.toArray((Object[]) Array.newInstance(type,buf.size())));
			ctx.releaseBuffer(buf);
		}
	}

	@Override
	public void list(AdaptContext ctx, int size) {
		if (size < 0) {
			ctx.setRef(ctx.objBuffer());
			ctx.fieldId = -1;
		} else {
			ctx.setRef(Array.newInstance(type,size));
			ctx.fieldId = 0;
		}

		ctx.push(ser);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void read(AdaptContext ctx, Object o) {
		if (ctx.ref == null) {
			if (o == null) {
				ctx.popd(true);
				return;
			}
			throw new IllegalStateException("ILS:"+o);
		}

		if (ctx.fieldId == -1) {
			((ArrayList<Object>) ctx.ref).add(o);
		} else {
			((Object[]) ctx.ref)[ctx.fieldId++] = o;
		}

		ctx.fieldState = 1;
		ctx.push(ser);
	}

	@Override
	public void write(CVisitor c, Object o) {
		if (o == null) c.valueNull();
		else {
			Object[] arr = ((Object[]) o);
			c.valueList(arr.length);
			for (Object o1 : arr) ser.write(c, o1);
			c.pop();
		}
	}
}