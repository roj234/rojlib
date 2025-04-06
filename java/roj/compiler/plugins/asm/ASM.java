package roj.compiler.plugins.asm;

import roj.asm.insn.CodeWriter;

import java.util.function.Consumer;

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
	 * 获取通过编译器参数或插件注入的'属性'，虽然方法签名是泛型，但实际值可以是任意表达式，并且可能和def类型不同
	 */
	public static <T> T inject(String name, T def) {return def;}

	/**
	 * 在编译期间修改生成的字节码，若ASM插件已加载，它将返回false常量，你可以用它实现条件编译，这个lambda表达式无法使用方法中的变量，并且会在受限环境中执行
	 * @return true => 不在Lavac环境中
	 */
	public static boolean asm(Consumer<CodeWriter> asm) {return true;}

	/**
	 * 泛型类型的无条件转换，例如将List&lt;String&gt;转换为List&lt;Integer&gt;
	 * @see roj.util.Helpers#cast(Object)
	 */
	@SuppressWarnings("unchecked")
	public static <T> T cast(Object input) {return (T) input;}
}