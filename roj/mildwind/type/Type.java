package roj.mildwind.type;

/**
 * @author Roj234
 * @since 2021/5/28 12:18
 */
public enum Type {
	STRING, INT, DOUBLE, NAN, BOOL, NULL, UNDEFINED, OBJECT, ARRAY, FUNCTION, SYMBOL;

	public static final Type[] VALUES = values();

	public String typeof() {
		switch (this) {
			case SYMBOL: return "symbol";
			case UNDEFINED: return "undefined";
			case BOOL: return "boolean";
			case NAN: case DOUBLE: case INT: return "number";
			default:
			case OBJECT: case ARRAY: case NULL: return "object";
			case STRING: return "string";
			case FUNCTION: return "function";
		}
	}

	public boolean primitive() { return ordinal() <= 4; }
	public boolean object() { return ordinal() >= 7; }
	public boolean num() { return this==INT||this==DOUBLE; }
	public boolean numOrBool() { return this==INT||this==DOUBLE||this==BOOL; }
}
