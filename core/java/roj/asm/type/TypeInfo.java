package roj.asm.type;

import roj.collect.HashMap;

import java.util.Locale;

import static roj.asm.type.Type.*;

/**
 * @author Roj234
 * @since 2025/09/17 19:51
 */
final class TypeInfo {
	final String desc;
	final String name;
	final String capitalizedName;
	final Type singleton;
	final byte opShift;
	final String opPrefix;
	final byte sort;
	final Type wrapper;
	final byte arrayType;

	private TypeInfo(String desc, String name, Type singleton, int opShift, String opPrefix, int sort, Type wrapper, int arrayType) {
		this.desc = desc;
		this.name = name.toLowerCase(Locale.ROOT).intern();
		this.capitalizedName = name;
		this.singleton = singleton;
		this.opShift = (byte) opShift;
		this.opPrefix = opPrefix;
		this.sort = (byte) sort;
		this.wrapper = wrapper;
		this.arrayType = (byte) arrayType;

		byName.put(this.name, this);
	}

	static final TypeInfo[] byId = new TypeInfo[26];
	static final HashMap<String, TypeInfo> byName = new HashMap<>(8);
	static final byte[] bySort = {VOID,BOOLEAN,BYTE,CHAR,SHORT,INT,LONG,FLOAT,DOUBLE};

	static {
		byId[BYTE-BYTE] = new TypeInfo("B", "Byte", new Type(BYTE), 0, "I", SORT_BYTE, klass("java/lang/Byte"), 8);
		byId[CHAR-BYTE] = new TypeInfo("C", "Char", new Type(CHAR), 0, "I", SORT_CHAR, klass("java/lang/Character"), 5);
		byId[FLOAT-BYTE] = new TypeInfo("F", "Float", new Type(FLOAT), 2, "F", SORT_FLOAT, klass("java/lang/Float"), 6);
		byId[DOUBLE-BYTE] = new TypeInfo("D", "Double", new Type(DOUBLE), 3, "D", SORT_DOUBLE, klass("java/lang/Double"), 7);
		byId[INT-BYTE] = new TypeInfo("I", "Int", new Type(INT), 0, "I", SORT_INT, klass("java/lang/Integer"), 10);
		byId[LONG-BYTE] = new TypeInfo("J", "Long", new Type(LONG), 1, "L", SORT_LONG, klass("java/lang/Long"), 11);
		byId[OBJECT -BYTE] = new TypeInfo("L", "Object", klass("java/lang/Object"), 4, "A", SORT_OBJECT, null, -1);
		byId[SHORT-BYTE] = new TypeInfo("S", "Short", new Type(SHORT), 0, "I", SORT_SHORT, klass("java/lang/Short"), 9);
		byId[VOID-BYTE] = new TypeInfo("V", "Void", new Type(VOID), 5, null, SORT_VOID, klass("java/lang/Void"), -1);
		byId[BOOLEAN-BYTE] = new TypeInfo("Z", "Boolean", new Type(BOOLEAN), 0, "I", SORT_BOOLEAN, klass("java/lang/Boolean"), 4);
	}
}
