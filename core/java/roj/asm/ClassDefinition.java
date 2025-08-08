package roj.asm;

import org.jetbrains.annotations.Nullable;
import roj.asm.cp.ConstantPool;
import roj.util.DynByteBuf;

import java.util.List;

/**
 * @author solo6975
 * @since 2021/7/22 18:20
 */
public interface ClassDefinition extends Attributed {
	@Nullable default ConstantPool cp() {return null;}

	String name();
	default void name(String name) {throw new UnsupportedOperationException(getClass().getName()+"是只读的");}
	String parent();
	default void parent(String parent) {throw new UnsupportedOperationException(getClass().getName()+"是只读的");}
	List<String> interfaces();

	List<? extends Member> fields();
	List<? extends Member> methods();

	default int getMethod(String name, String desc) {
		var methods = methods();
		for (int i = 0, size = methods.size(); i < size; i++) {
			Member ms = methods.get(i);
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
			Member fs = fields.get(i);
			if (key.equals(fs.name())) return i;
		}
		return -1;
	}

	default DynByteBuf toByteArray(DynByteBuf buf) {throw new UnsupportedOperationException(getClass().getName()+"不支持序列化");}
}