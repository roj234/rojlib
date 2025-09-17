package roj.asm.type;

import roj.collect.HashMap;

import java.util.Locale;

import static roj.asm.type.Type.*;

/**
 * @author Roj234
 * @since 2025/09/17 19:51
 */
final class R {
	final String desc;
	final String name;
	final String capitalizedName;
	final Type singleton;
	final byte opShift;
	final String opPrefix;
	final byte sort;

	R(String desc, String name, Type singleton, int opShift, String opPrefix, int sort) {
		this.desc = desc;
		this.name = name.toLowerCase(Locale.ROOT).intern();
		this.capitalizedName = name;
		this.singleton = singleton;
		this.opShift = (byte) opShift;
		this.opPrefix = opPrefix;
		this.sort = (byte) sort;

		byName.put(this.name, this);
	}

	static final R[] byId = new R[26];
	static final HashMap<String, R> byName = new HashMap<>(8);
	static final byte[] bySort = {VOID,BOOLEAN,BYTE,CHAR,SHORT,INT,LONG,FLOAT,DOUBLE};

	static {
		byId[BYTE-BYTE] = new R("B", "Byte", new Type(BYTE), 0, "I", SORT_BYTE);
		byId[CHAR-BYTE] = new R("C", "Char", new Type(CHAR), 0, "I", SORT_CHAR);
		byId[FLOAT-BYTE] = new R("F", "Float", new Type(FLOAT), 2, "F", SORT_FLOAT);
		byId[DOUBLE-BYTE] = new R("D", "Double", new Type(DOUBLE), 3, "D", SORT_DOUBLE);
		byId[INT-BYTE] = new R("I", "Int", new Type(INT), 0, "I", SORT_INT);
		byId[LONG-BYTE] = new R("J", "Long", new Type(LONG), 1, "L", SORT_LONG);
		byId[CLASS-BYTE] = new R("L", "Object", null, 4, "A", SORT_OBJECT);
		byId[SHORT-BYTE] = new R("S", "Short", new Type(SHORT), 0, "I", SORT_SHORT);
		byId[VOID-BYTE] = new R("V", "Void", new Type(VOID), 5, null, SORT_VOID);
		byId[BOOLEAN-BYTE] = new R("Z", "Boolean", new Type(BOOLEAN), 0, "I", SORT_BOOLEAN);
	}
}
