package roj.compiler.api;

import roj.asm.type.Type;

/**
 * @author Roj234
 * @since 2025/2/3 0003 22:15
 */
public interface Types {
	Type OBJECT_TYPE = Type.klass("java/lang/Object");
	Type STRING_TYPE = Type.klass("java/lang/String");
	Type THROWABLE_TYPE = Type.klass("java/lang/Throwable");
	Type VOID_TYPE = Type.klass("java/lang/Void");
	Type ITERATOR_TYPE = Type.klass("java/util/Iterator");
	Type CHARSEQUENCE_TYPE = Type.klass("java/lang/CharSequence");
	Type RUNTIME_EXCEPTION = Type.klass("java/lang/RuntimeException");
	Type ERROR = Type.klass("java/lang/Error");
}
