package roj.config.data;

/**
 * Config Type
 *
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public enum Type {
	LIST, MAP, STRING, NULL, BOOL, Int1, Int2, INTEGER, LONG, Float4, DOUBLE, DATE;

	public static final Type[] VALUES = values();

	public boolean isContainer() { return ordinal() <= 1; }
	public boolean isNumber() { return ordinal() >= Int1.ordinal(); }

	@Deprecated
	public boolean isSimilar(Type type) {
		if (this == type) return type != NULL;

		return switch (this) {
			case STRING, BOOL, INTEGER, LONG, DOUBLE, DATE, Int1, Int2, Float4 -> switch (type) {
				case STRING, BOOL, INTEGER, LONG, DOUBLE, Int1, Int2, Float4 -> true;
				default -> false;
			};
			default -> false;
		};
	}
}