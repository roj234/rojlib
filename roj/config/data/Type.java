package roj.config.data;

/**
 * Config Type
 *
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public enum Type {
	LIST, MAP, STRING, NULL, BOOL, INTEGER, LONG, DOUBLE, DATE, Int1, Int2, Float4;

	public static final Type[] VALUES = values();

	public boolean isNumber() {
		return ordinal() >= INTEGER.ordinal();
	}

	public boolean isSimilar(Type type) {
		if (this == type) return type != NULL;

		switch (this) {
			case STRING:
			case BOOL:
			case INTEGER:
			case LONG:
			case DOUBLE:
			case DATE:
			case Int1:
			case Int2:
			case Float4:
				switch (type) {
					case STRING:
					case BOOL:
					case INTEGER:
					case LONG:
					case DOUBLE:
					case Int1:
					case Int2:
					case Float4: return true;
					default: return false;
				}
			default: return false;
		}
	}

	public boolean isContainer() {
		return this == MAP || this == LIST;
	}
}
