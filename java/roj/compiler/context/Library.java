package roj.compiler.context;

import org.jetbrains.annotations.Nullable;
import roj.asm.ClassNode;
import roj.asm.FieldNode;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.attr.Attribute;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Roj234
 * @since 2022/9/16 21:51
 */
public interface Library {
	default String moduleName() {
		var data = get("module-info");
		if (data != null) {
			var module = data.getAttribute(data.cp, Attribute.Module);
			return module.self.name;
		}
		return null;
	}

	/**
	 * 可选但不推荐不覆盖
	 */
	default Collection<String> content() {return Collections.emptySet();}

	default boolean hasPackageCache() {return false;}
	/**
	 * 如果是空的那么不支持自动导入
	 */
	default Collection<String> getPackageCache() {return content();}

	@Nullable ClassNode get(CharSequence name);

	@Nullable default InputStream getResource(CharSequence name) throws IOException {return null;}
	default void close() throws Exception {}

	static void removeUnrelatedAttribute(ClassNode data) {
		var list = data.attributesNullable();
		if (list != null) {
			list.removeByName("NestMembers");
			list.removeByName("NestHost");
			list.removeByName("BootstrapMethods");
			list.removeByName("SourceFile");
			list.removeByName("EnclosingMethod");
		}

		List<MethodNode> methods = data.methods();
		for (int i = 0; i < methods.size(); i++) {
			var method = methods.get(i);
			var attributes = method.attributesNullable();
			if (attributes != null) {
				if ((method.modifier & Opcodes.ACC_PRIVATE) != 0) attributes.clear();
				else attributes.removeByName("Code");
			}
		}

		List<FieldNode> fields = data.fields();
		for (int i = 0; i < fields.size(); i++) {
			var method = fields.get(i);
			var attributes = method.attributesNullable();
			if (attributes != null) {
				if ((method.modifier & Opcodes.ACC_PRIVATE) != 0) attributes.clear();
			}
		}
	}
}