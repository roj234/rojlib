package roj.compiler;

public interface CompilerSpec {
	int ATTR_METHOD_PARAMETERS = 0;
	int ATTR_SOURCE_FILE = 1;
	int ATTR_LINE_NUMBERS = 2;
	int ATTR_LOCAL_VARIABLES = 3;
	int ATTR_INNER_CLASS = 4;
	int ATTR_STACK_FRAME = 5;
	int EXTRA_DEBUG_INFO = 6;
	int OPTIONAL_SEMICOLON = 8;
	int SHARED_ARRAY_STRING_CONCAT = 9;
	int DISABLE_ASSERT = 10;


	// no stack frame
	int COMPATIBILITY_LEVEL_JAVA_6 = 6;
	// lambda
	int COMPATIBILITY_LEVEL_JAVA_8 = 8;
	// module
	int COMPATIBILITY_LEVEL_JAVA_9 = 9;
	// constant_dynamic
	int COMPATIBILITY_LEVEL_JAVA_11 = 11;
	// record
	int COMPATIBILITY_LEVEL_JAVA_17 = 17;
	// string template via lambda
	int COMPATIBILITY_LEVEL_JAVA_21 = 21;
}