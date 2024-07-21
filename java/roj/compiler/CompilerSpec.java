package roj.compiler;

import roj.text.CharList;

public interface CompilerSpec {
	// not change code
	int ATTR_METHOD_PARAMETERS = 0;
	int ATTR_SOURCE_FILE = 1;
	int ATTR_LINE_NUMBERS = 2;
	int ATTR_LOCAL_VARIABLES = 3;
	int ATTR_INNER_CLASS = 4;
	int ATTR_STACK_FRAME = 5;
	int OPTIONAL_SEMICOLON = 6;
	int VERIFY_FILENAME = 7;
	// change bytecode
	/** Use {@link CharList#_free()} instead of StringBuilder */
	int SHARED_STRING_CONCAT = 10;
	/** (Java11) <a href="https://www.baeldung.com/java-nest-based-access-control">Nest based access control</a> */
	int NESTED_MEMBER = 11;
	/** Dont compile assert statements */
	int DISABLE_ASSERT = 12;
	/** (Java17) Make enum classes sealed if they're abstract */
	int SEALED_ENUM = 13;
	/** Turn Unreported Exception to Warning */
	int CHECKED_EXCEPTION = 14;


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