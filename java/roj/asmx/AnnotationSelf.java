package roj.asmx;

import roj.asm.type.Type;
import roj.collect.MyHashMap;
import roj.config.data.CEntry;

public class AnnotationSelf {
	public String name;

	public boolean stackable;
	public String repeatOn = null;
	public int applicableTo = -1;
	public byte kind = 0;

	public MyHashMap<String, CEntry> values = new MyHashMap<>();
	public MyHashMap<String, Type> types = new MyHashMap<>();

	public String repeatOn() { return repeatOn; }

	public static final int TYPE = 1 << 2, FIELD = 1 << 3, METHOD = 1 << 4, PARAMETER = 1 << 5, CONSTRUCTOR = 1 << 6, LOCAL_VARIABLE = 1 << 7, ANNOTATION_TYPE = 1 << 8, PACKAGE = 1 << 9, // 1.8
	TYPE_PARAMETER = 1 << 10, TYPE_USE = 1 << 11, MODULE = 1 << 12, RECORD_COMPONENT = 1 << 13;

	// bit set
	public int applicableTo() { return applicableTo; }

	public static final int SOURCE = 1, CLASS = 0, RUNTIME = 2;
	public int kind() { return kind; }
}