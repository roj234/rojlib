package roj.compiler.plugins.asm;

/**
 * @author Roj234
 * @since 2023/9/23 0023 19:21
 */
public class ASM {
	public static final int TARGET_JAVA_VERSION;
	static {
		// 如果使用Lavac，就不会用到这个了
		String v = System.getProperty("java.version");
		String major = v.substring(0, v.indexOf('.'));
		TARGET_JAVA_VERSION = Integer.parseInt(major);
	}

	/**
	 * 获取注入的属性
	 * @return 返回类型为属性或def的真实类型，不一定是对象，并且可能随属性注入改变
	 */
	public static <T> T inject(String name, T def) {return def;}

	/**
	 * 执行无返回值的操作，语法为AsmLang
	 * @param asm 常量
	 * @return true => 不在Lavac环境中
	 */
	public static boolean __asm(String asm) {return true;}
	//public static void __asmx(Object... asm) {}

	/**
	 * 强制的 <b>泛型</b> 转换
	 * @see roj.util.Helpers#cast(Object)
	 */
	@SuppressWarnings("unchecked")
	public static <T> T cast(Object input) {return (T) input;}

	/**
	 * 解释为<none>
	 */
	public static boolean i2z(int v) { return v != 0; }
	/**
	 * 解释为<none>
	 */
	public static int z2i(boolean b) { return b?1:0; }
	/**
	 * 解释为POP或POP2
	 */
	public static void pop(Object input) {}
}