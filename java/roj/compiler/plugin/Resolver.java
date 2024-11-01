package roj.compiler.plugin;

import roj.asm.tree.ConstantData;
import roj.compiler.context.Library;

/**
 * @author Roj234
 * @since 2024/5/21 2:47
 */
public interface Resolver {
	/**
	 * 触发时机: Library中的类第一次被加载入GlobalContext时<br>
	 * 用途: 修改某些类中的方法, 仅在需要用到时, 或者作为类加载限制的兜底措施(虽然我觉得package-restricted足够了)<br>
	 * 如果无论是否加载都要修改，请看{@link roj.compiler.context.GlobalContext#addLibrary(Library)}
	 */
	ConstantData classResolved(ConstantData info);
}