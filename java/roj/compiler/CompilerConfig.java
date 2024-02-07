package roj.compiler;

public interface CompilerConfig {
	int KEEP_PARAMETER_NAME = 0;
	int SOURCE_FILE = 1;
	int DEFAULT_VALUE = 2;
	int PRE_COMPILE = 5;
	int PRIMITIVE_GENERIC = 6;
	int INLINE_ASM = 7;
	int CONST = 12;
	int INTERFACE_INACCESSIBLE_FIELD = 16;
	public static final int ADVANCED_GENERIC_CHECK = 32;
	// map.put({
	// 		key: "key",
	//      val: "val"
	// });
}