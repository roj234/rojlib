package roj.config.auto;

import roj.asm.type.Type;
import roj.config.serial.CVisitor;

/**
 * @author Roj234
 * @since 2023/3/23 18:18
 */
final class PrimObj extends Adapter {
	static final PrimObj STR = new PrimObj(Type.CLASS);

	private final byte type;
	PrimObj(int type) {this.type = (byte) type;}

	public void read(AdaptContext ctx, float l) {read(ctx, (Float) l);}
	@Override public void read(AdaptContext ctx, Object o) {
		assert ctx.fieldId == -2;

		if (o instanceof Number n) {
			switch (type) {
				case Type.LONG -> o = n.longValue();
				case Type.DOUBLE -> o = n.doubleValue();
				case Type.FLOAT -> o = n.floatValue();
				case Type.BYTE -> o = n.byteValue();
				case Type.SHORT -> o = n.shortValue();
				case Type.INT -> o = n.intValue();
				case Type.CLASS -> o = n.toString();
				default -> throw new IllegalArgumentException("Cannot convert "+o+" to "+(char)type);
			}
		} else {
			var s = String.valueOf(o);
			switch (type) {
				case Type.BOOLEAN -> o = Boolean.parseBoolean(s);
				case Type.LONG -> o = Long.parseLong(s);
				case Type.DOUBLE -> o = Double.parseDouble(s);
				case Type.FLOAT -> o = Float.parseFloat(s);
				case Type.BYTE -> o = Byte.parseByte(s);
				case Type.SHORT -> o = Short.parseShort(s);
				case Type.INT -> o = Integer.parseInt(s);
				case Type.CHAR -> o = o.toString().charAt(0);
				case Type.CLASS -> o = o == null ? null : s;
				default -> throw new IllegalArgumentException("Cannot convert "+o+" to "+(char)type);
			}
		}

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
			case Type.CLASS -> c.value(o.toString());
			case Type.BOOLEAN -> c.value((Boolean) o);
			case Type.LONG -> c.value(((Number) o).longValue());
			case Type.DOUBLE -> c.value(((Number) o).doubleValue());
			case Type.FLOAT -> c.value(((Number) o).floatValue());
			case Type.BYTE -> c.value(((Number) o).byteValue());
			case Type.SHORT -> c.value(((Number) o).shortValue());
			case Type.INT -> c.value(((Number) o).intValue());
			case Type.CHAR -> c.value((Character) o);
		}
	}
}