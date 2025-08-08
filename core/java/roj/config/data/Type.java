package roj.config.data;

/**
 * Config Type
 *
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public enum Type {
	LIST, MAP, STRING, NULL, BOOL, Int1, Int2, INTEGER, LONG, Float4, DOUBLE, DATE,
	CHAR, OTHER;

	public static final Type[] VALUES = values();

	public boolean isContainer() {return ordinal() <= 1;}
	public boolean isNumber() {return ordinal() >= Int1.ordinal();}

	public char symbol() {return switch (this) {
		case LIST -> '[';
		case MAP -> '@';
		case STRING -> 's';
		case BOOL -> 'Z';
		case Int1 -> 'B';
		case Int2 -> 'S';
		case CHAR -> 'C';
		case INTEGER -> 'I';
		case LONG -> 'J';
		case Float4 -> 'F';
		case DOUBLE -> 'D';
		default -> '\0';
	};}
}