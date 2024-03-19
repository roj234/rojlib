package roj.compiler.asmlang;

/**
 * @author Roj234
 * @since 2023/9/23 0023 19:21
 */
public class ASM {
	public static boolean __asm(String asm) {return true;}

	public static boolean i2z(int v) { return v != 0; }
	public static int z2i(boolean b) { return b?1:0; }
}