package roj.staging.new_omc;

import roj.reflect.Unsafe;
import roj.util.DynByteBuf;

import static roj.reflect.Unsafe.U;

/**
 * @author Roj234
 * @since 2026/02/06 21:16
 */
final class PrimitiveArrayAdapter implements TypeAdapter.ArrayAdapter {
	private static final State.Field TYPE = State.Field.primitive(5);

	@Override
	public Object newContainerBuilder(int size) {
		return size < 0 ? DynByteBuf.allocateDirect(4096, 104857600) : (double[]) U.allocateUninitializedArray(double.class, size);
	}

	@Override
	public Object build(Object container, ValueContainer value) {
		if (container instanceof DynByteBuf buf) {
			var array = (double[]) U.allocateUninitializedArray(double.class, buf.readableBytes() >>> 3);
			Unsafe.U.copyMemory(null, buf.address(), array, Unsafe.ARRAY_INT_BASE_OFFSET, buf.readableBytes());
			return array;
		}
		return container;
	}

	@Override
	public TypeAdapter set(ValueContainer container, ValueContainer key, ValueContainer value) {
		Object arr = container.LValue;
		if (arr instanceof DynByteBuf buf) {
			buf.wIndex((key.IValue + 1) << 3);
			Unsafe.U.putDouble(null, buf.address() + ((long) key.IValue << 3), value.DValue); // native byte order
		} else {
			((double[]) arr)[key.IValue] = value.DValue;
		}
		return null;
	}

	@Override
	public boolean isPrimitiveArray(int sort) {return sort == 5;}

	@Override
	public State.Field getType(int index) {return TYPE;}
}
