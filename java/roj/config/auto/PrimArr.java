package roj.config.auto;

import roj.config.serial.CVisitor;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2023/3/19 0019 20:31
 */
final class PrimArr extends Adapter {
	@Override
	public void read(AdaptContext ctx, Object o) {
		if (o != null) super.read(ctx, o);
		else ctx.popd(true);
	}

	@Override
	public void pop(AdaptContext ctx) {
		if (ctx.fieldState == 0) {
			DynByteBuf buf = (DynByteBuf) ctx.ref;
			long[] arr = new long[ctx.fieldId];
			for (int i = 0; i < arr.length; i++) {
				arr[i] = buf.readLong();
			}
			ctx.setRef(arr);
			ctx.fieldState = 1;
		}
	}

	@Override
	public void list(AdaptContext ctx, int size) {
		if (size < 0) {
			ctx.setRef(ctx.buffer());
			ctx.fieldState = 0;
		} else {
			ctx.setRef(new long[size]);
			ctx.fieldState = 1;
		}
		ctx.fieldId = 0;
	}

	@Override
	public void read(AdaptContext ctx, long l) {
		if (ctx.fieldState == 0) {
			((DynByteBuf) ctx.ref).putLong(l);
		} else {
			((long[]) ctx.ref)[ctx.fieldId] = l;
		}
		ctx.fieldId++;
	}

	@Override
	public void write(CVisitor c, Object o) {
		if (o == null) c.valueNull();
		else {
			long[] arr = ((long[]) o);
			c.valueList(arr.length);
			for (long l : arr) c.value(l);
			c.pop();
		}
	}
	public void write1(CVisitor c, Object o) {
		if (o == null) c.valueNull();
		else c.value(((long[]) o));
	}
}