package roj.compiler.api;

import roj.asm.type.Type;

/**
 * @author Roj234
 * @since 2025/2/3 0003 22:15
 */
public interface Types {
	Type OBJECT_TYPE = new Type("java/lang/Object");
	Type STRING_TYPE = new Type("java/lang/String");
	Type THROWABLE_TYPE = new Type("java/lang/Throwable");
	Type VOID_TYPE = new Type("java/lang/Void");
	Type ITERATOR_TYPE = new Type("java/util/Iterator");
	Type CHARSEQUENCE_TYPE = new Type("java/lang/CharSequence");
	Type RUNTIME_EXCEPTION = new Type("java/lang/RuntimeException");
}
