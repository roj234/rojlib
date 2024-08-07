package roj.asm.tree;

import org.jetbrains.annotations.Nullable;
import roj.asm.cp.ConstantPool;
import roj.util.DynByteBuf;

import java.util.List;

/**
 * Abstract Class
 *
 * @author solo6975
 * @since 2021/7/22 18:20
 */
public interface IClass extends Attributed {
	String name();
	default void name(String name) {throw new UnsupportedOperationException(getClass().getName() + " is readonly");}
	@Nullable
	String parent();
	default void parent(String parent) {throw new UnsupportedOperationException(getClass().getName() + " is readonly");}
	List<String> interfaces();

	List<? extends RawNode> methods();
	List<? extends RawNode> fields();

	default int getMethod(String name, String desc) {
		var methods = methods();
		for (int i = 0, size = methods.size(); i < size; i++) {
			RawNode ms = methods.get(i);
			if (name.equals(ms.name()) && (desc == null || ms.rawDesc().equals(desc))) {
				return i;
			}
		}
		return -1;
	}
	default int getMethod(String name) {return getMethod(name, null);}
	default int getField(CharSequence key) {
		var fields = fields();
		for (int i = 0, size = fields.size(); i < size; i++) {
			RawNode fs = fields.get(i);
			if (key.equals(fs.name())) return i;
		}
		return -1;
	}

	default DynByteBuf getBytes(DynByteBuf buf) {throw new UnsupportedOperationException(getClass().getName() + " cannot encode");}
	default ConstantPool cp() {return null;}
}