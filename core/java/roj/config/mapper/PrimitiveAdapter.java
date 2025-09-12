package roj.config.mapper;

import roj.asm.type.Type;
import roj.config.ValueEmitter;

/**
 * @author Roj234
 * @since 2023/3/23 18:18
 */
final class PrimitiveAdapter extends TypeAdapter {
	static final PrimitiveAdapter STR = new PrimitiveAdapter(Type.CLASS);

	private final byte type;
	PrimitiveAdapter(int type) {this.type = (byte) type;}

	public void read(MappingContext ctx, float l) {read(ctx, (Float) l);}
	@Override public void read(MappingContext ctx, Object o) {
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
	public void write(ValueEmitter c, Object o) {
		if (o == null) {
			c.emitNull();
			return;
		}

		switch (type) {
			case Type.CLASS -> c.emit(o.toString());
			case Type.BOOLEAN -> c.emit((Boolean) o);
			case Type.LONG -> c.emit(((Number) o).longValue());
			case Type.DOUBLE -> c.emit(((Number) o).doubleValue());
			case Type.FLOAT -> c.emit(((Number) o).floatValue());
			case Type.BYTE -> c.emit(((Number) o).byteValue());
			case Type.SHORT -> c.emit(((Number) o).shortValue());
			case Type.INT -> c.emit(((Number) o).intValue());
			case Type.CHAR -> c.emit((Character) o);
		}
	}
}