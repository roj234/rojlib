package roj.config.auto;

import roj.asm.type.Type;
import roj.config.serial.CVisitor;

/**
 * @author Roj234
 * @since 2023/3/23 0023 18:18
 */
final class PrimObj extends Adapter {
	private final byte type;
	PrimObj(int type) {
		this.type = (byte) type;
	}

	@Override
	public void read(AdaptContext ctx, Object o) {
		assert ctx.fieldId == -2;

		ctx.setRef(o);
		ctx.popd(true);
	}

	@Override
	public void write(CVisitor c, Object o) {
		if (o == null) {
			c.valueNull();
			return;
		}

		switch (type) {
			case Type.CLASS: c.value(o.toString()); break;
			case Type.BOOLEAN: c.value((Boolean) o); break;
			case Type.LONG: c.value((Long) o); break;
			case Type.DOUBLE: c.value(((Number) o).doubleValue()); break;
			case Type.FLOAT: c.value(((Number) o).floatValue()); break;
			case Type.BYTE: c.value(((Byte) o)); break;
			case Type.SHORT: c.value(((Short) o)); break;
			case Type.INT: c.value(((Number) o).intValue()); break;
			case Type.CHAR: c.value((Character) o); break;
		}
	}
}