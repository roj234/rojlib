package roj.config.mapper;

import roj.config.ValueEmitter;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2023/3/19 20:31
 */
final class PrimitiveArrayAdapter extends TypeAdapter {
	@Override
	public void read(MappingContext ctx, Object o) {
		if (o != null) super.read(ctx, o);
		else ctx.popd(true);
	}

	@Override
	public void pop(MappingContext ctx) {
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
	public void list(MappingContext ctx, int size) {
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
	public void read(MappingContext ctx, long l) {
		if (ctx.fieldState == 0) {
			((DynByteBuf) ctx.ref).putLong(l);
		} else {
			((long[]) ctx.ref)[ctx.fieldId] = l;
		}
		ctx.fieldId++;
	}

	@Override
	public void write(ValueEmitter c, Object o) {
		if (o == null) c.emitNull();
		else {
			long[] arr = ((long[]) o);
			c.emitList(arr.length);
			for (long l : arr) c.emit(l);
			c.pop();
		}
	}
	public void write1(ValueEmitter c, Object o) {
		if (o == null) c.emitNull();
		else c.emit(((long[]) o));
	}
}