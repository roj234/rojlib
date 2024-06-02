package roj.compiler.plugins.asm;

/**
 * @author Roj234
 * @since 2023/9/23 0023 19:21
 */
public class ASM {
	/**
	 * Get injected properties
	 */
	// Not implemented
	public static <T> T inject(String name, T def) {return def;}

	public static boolean __asm(String asm) {return true;}

	// Not implemented
	@SuppressWarnings("unchecked")
	public static <T> T cast(Object input) {return (T) input;}
	public static boolean i2z(int v) { return v != 0; }
	public static int z2i(boolean b) { return b?1:0; }
}