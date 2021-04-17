package roj.lavac.api;

import org.intellij.lang.annotations.MagicConstant;
import roj.asm.Opcodes;

/**
 * ASM by call
 *
 * @author Roj233
 * @since 2021/9/2 21:53
 */
public class ASM {
	/**
	 * No parameter ASM
	 *
	 * @param opcode Opcode
	 */
	public static native void simple(@MagicConstant(valuesFromClass = Opcodes.class) byte opcode);

	public static native void simpleX(@MagicConstant(valuesFromClass = Opcodes.class) byte opcode, int p1);

	public static native void simpleX2(@MagicConstant(valuesFromClass = Opcodes.class) byte opcode, int p1, int p2);

	public static native Object load(Object from);

	public static native Object load_by_index(int index);

	public static native Object loadThis();

	public static native void store_pop(Object target);

	public static native void store(Object target, Object value);

	public static native void store_by_index(int index, Object value);

	public static native void keep_return_value(Object returnValue);

	public static native void assert_stack(int stackSize);

	public static native void assert_stack(int stackSize, String message);

	public static native void assert_local(int localSize);

	public static native void assert_local(int localSize, String message);

	public static native void assert_local_type(int localIndex, Object type);

	public static native void assert_local_type(int localIndex, Object type, String message);
}
