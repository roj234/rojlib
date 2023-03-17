package roj.asm.tree;

import roj.asm.cst.ConstantPool;
import roj.util.DynByteBuf;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Abstract Class
 *
 * @author solo6975
 * @since 2021/7/22 18:20
 */
public interface IClass extends Attributed {
	String name();
	default void name(String name) {
		throw new UnsupportedOperationException(getClass().getName() + " does not support name(String)");
	}
	@Nullable
	String parent();
	default void parent(String parent) {
		throw new UnsupportedOperationException(getClass().getName() + " does not support parent(String)");
	}
	List<String> interfaces();

	List<? extends MoFNode> methods();
	List<? extends MoFNode> fields();

	default int getMethod(CharSequence name, CharSequence desc) {
		List<? extends MoFNode> methods = methods();
		for (int i = 0; i < methods.size(); i++) {
			MoFNode ms = methods.get(i);
			if (name.equals(ms.name()) && (desc == null || desc.equals(ms.rawDesc()))) {
				return i;
			}
		}
		return -1;
	}
	default int getMethod(CharSequence name) {
		return getMethod(name, null);
	}
	default int getField(CharSequence key) {
		List<? extends MoFNode> fields = fields();
		for (int i = 0; i < fields.size(); i++) {
			MoFNode fs = fields.get(i);
			if (key.equals(fs.name())) return i;
		}
		return -1;
	}

	default DynByteBuf getBytes(DynByteBuf buf) {
		throw new UnsupportedOperationException(getClass().getName() + " does not support encoding");
	}
	default ConstantPool cp() {
		return null;
	}
}
