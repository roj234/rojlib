package roj.kscript.type;

import roj.concurrent.OperationDone;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/28 12:18
 */
public enum Type {
	ARRAY, OBJECT, STRING, INT, NULL, BOOL, DOUBLE, FUNCTION, UNDEFINED, ERROR, JAVA_OBJECT, LONG, FLOAT;

	public static final Type[] VALUES = values();

	public String typeof() {
		switch (this) {
			case UNDEFINED:
				return "undefined";
			case BOOL:
				return "boolean";
			case DOUBLE:
			case INT:
				return "number";
			case JAVA_OBJECT:
			case OBJECT:
			case ARRAY:
			case NULL:
			case ERROR:
				return "object";
			case STRING:
				return "string";
			case FUNCTION:
				return "function";
		}
		throw OperationDone.NEVER;
	}

}
