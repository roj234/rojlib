package roj.compiler.context;

import org.jetbrains.annotations.Nullable;
import roj.asm.tree.ConstantData;
import roj.asm.tree.attr.Attribute;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Roj234
 * @since 2022/9/16 0016 21:51
 */
public interface Library {
	/**
	 * 文件修改后，该值必须不同以更新缓存
	 * 如果任意为null，那么将永远作废缓存
	 */
	default String versionCode() {return null;}
	default String moduleName() {
		var data = get("module-info");
		if (data != null) {
			var module = data.parsedAttr(data.cp, Attribute.Module);
			return module.self.name;
		}
		return null;
	}

	/**
	 * 可选但不推荐，如果是空的那么不支持自动导入
	 */
	default Collection<String> content() {return Collections.emptySet();}
	@Nullable ConstantData get(CharSequence name);

	@Nullable default InputStream getResource(CharSequence name) throws IOException {return null;}
	default void close() throws Exception {}
}