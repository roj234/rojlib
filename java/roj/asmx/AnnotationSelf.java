package roj.asmx;

public class AnnotationSelf {
	public String name;

	public String repeatOn = null;
	public int applicableTo = -1;
	public byte kind = 0;

	public String repeatOn() { return repeatOn; }

	public static final int TYPE = 1 << 2, FIELD = 1 << 3, METHOD = 1 << 4, PARAMETER = 1 << 5, CONSTRUCTOR = 1 << 6, LOCAL_VARIABLE = 1 << 7, ANNOTATION_TYPE = 1 << 8, PACKAGE = 1 << 9, // 1.8
	TYPE_PARAMETER = 1 << 10, TYPE_USE = 1 << 11;

	// bit set
	public int applicableTo() { return applicableTo; }

	public static final int SOURCE = 1, CLASS = 0, RUNTIME = 2;
	public int kind() { return kind; }
}