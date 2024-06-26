package roj.compiler;

public interface CompilerSpec {
	int KEEP_PARAMETER_NAME = 0;
	int SOURCE_FILE = 1;
	int PRE_COMPILE = 5;
	int PRIMITIVE_GENERIC = 6;
	int INTERFACE_INACCESSIBLE_FIELD = 16;
	int ADVANCED_GENERIC_CHECK = 32;
	int KOTLIN_SEMICOLON = 33;
	int DISABLE_RAW_TYPE = 114514;
}