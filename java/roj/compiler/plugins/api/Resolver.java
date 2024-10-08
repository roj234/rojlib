package roj.compiler.plugins.api;

import roj.asm.tree.ConstantData;
import roj.compiler.context.Library;

import java.util.List;

/**
 * @author Roj234
 * @since 2024/5/21 2:47
 */
public interface Resolver {
	/**
	 * 触发时机: {@link roj.compiler.context.GlobalContext#getClassInfo(CharSequence)}第一次返回之前 <br>
	 * 用途: 修改某些类中的方法, 仅在需要用到时, 或者作为类加载限制的兜底措施(虽然我觉得package-restricted足够了)<br>
	 * 如果无论是否加载都要修改，请看{@link roj.compiler.context.GlobalContext#addLibrary(Library)}
	 */
	default void classResolved(ConstantData info) {}

	/**
	 * 触发时机: {@link roj.compiler.context.GlobalContext#getAvailablePackages(String)}第一次返回之前 <br>
	 * 用途: 修改某些类中的方法, 仅在需要用到时, 或者作为类加载限制的兜底措施(虽然我觉得package-restricted足够了)<br>
	 * 如果无论是否加载都要修改，请看{@link roj.compiler.context.GlobalContext#addLibrary(Library)}
	 */
	default void packageListed(String shortName, List<String> packages) {}
}