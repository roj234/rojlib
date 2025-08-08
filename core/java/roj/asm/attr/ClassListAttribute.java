package roj.asm.attr;

import roj.asm.cp.Constant;
import roj.asm.cp.ConstantPool;
import roj.collect.ArrayList;
import roj.util.DynByteBuf;
import roj.util.TypedKey;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class ClassListAttribute extends Attribute {
	private static final int MODULE_PACKAGES_ID = NAMED_ID.getByValue("ModulePackages");
	private final byte type;

	public ClassListAttribute(TypedKey<ClassListAttribute> key) {this(key, new ArrayList<>());}
	public ClassListAttribute(TypedKey<ClassListAttribute> key, List<String> list) {
		type = (byte) NAMED_ID.getByValue(key.name);
		value = list;
	}

	public ClassListAttribute(String name, DynByteBuf r, ConstantPool pool) {
		type = (byte) NAMED_ID.getByValue(name);

		int len = r.readUnsignedShort();
		value = new ArrayList<>(len);
		while (len-- > 0) value.add(pool.getRefName(r, type == MODULE_PACKAGES_ID ? Constant.PACKAGE : Constant.CLASS));
	}

	public final List<String> value;

	@Override
	public boolean writeIgnore() { return value.isEmpty(); }
	@Override
	public String name() { return NAMED_ID.get(type); }
	@Override
	public void toByteArrayNoHeader(DynByteBuf w, ConstantPool pool) {
		List<String> list = value;
		w.putShort(list.size());

		if (type == MODULE_PACKAGES_ID) {
			for (int i = 0; i < list.size(); i++) w.putShort(pool.getPackageId(list.get(i)));
		} else {
			for (int i = 0; i < list.size(); i++) w.putShort(pool.getClassId(list.get(i)));
		}
	}

	public String toString() { return name()+": " + value; }
}