package roj.config.serial;

import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2023/3/19 0019 20:31
 */
final class PrimArr extends Adapter {
	@Override
	void pop(AdaptContext ctx) {
		if (ctx.fieldState == 0) {
			DynByteBuf buf = (DynByteBuf) ctx.ref;
			long[] arr = new long[ctx.fieldId];
			for (int i = 0; i < arr.length; i++) {
				arr[i] = buf.readLong();
			}
			ctx.ref = arr;
			ctx.fieldState = 1;
		}
	}

	@Override
	void list(AdaptContext ctx, int size) {
		if (size < 0) {
			ctx.ref = ctx.buffer();
			ctx.fieldState = 0;
		} else {
			ctx.ref = new long[size];
			ctx.fieldState = 1;
		}
		ctx.fieldId = 0;
	}

	@Override
	void read(AdaptContext ctx, long l) {
		if (ctx.fieldState == 0) {
			((DynByteBuf) ctx.ref).putLong(l);
		} else {
			((long[]) ctx.ref)[ctx.fieldId] = l;
		}
		ctx.fieldId++;
	}

	@Override
	void write(CVisitor c, Object o) {
		if (o == null) c.valueNull();
		else {
			long[] arr = ((long[]) o);
			c.valueList(arr.length);
			for (long l : arr) c.value(l);
			c.pop();
		}
	}
}
