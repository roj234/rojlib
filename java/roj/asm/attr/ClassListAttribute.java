package roj.asm.attr;

import roj.asm.cp.ConstantPool;
import roj.collect.SimpleList;
import roj.util.DynByteBuf;
import roj.util.TypedKey;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class ClassListAttribute extends Attribute {
	private static final int MODULE_PACKAGES_ID = NAMED_ID.getInt("ModulePackages");
	private final byte name;

	public ClassListAttribute(TypedKey<ClassListAttribute> key) {this(key, new SimpleList<>());}
	public ClassListAttribute(TypedKey<ClassListAttribute> key, List<String> list) {
		this.name = (byte) NAMED_ID.getInt(key.name);
		value = list;
	}

	public ClassListAttribute(String name, DynByteBuf r, ConstantPool pool) {
		this.name = (byte) NAMED_ID.getInt(name);

		int len = r.readUnsignedShort();
		value = new SimpleList<>(len);
		while (len-- > 0) value.add(pool.getRefName(r));
	}

	public final List<String> value;

	@Override
	public boolean writeIgnore() { return value.isEmpty(); }
	@Override
	public String name() { return NAMED_ID.get(name); }
	@Override
	public void toByteArrayNoHeader(DynByteBuf w, ConstantPool pool) {
		List<String> list = value;
		w.putShort(list.size());

		if (name == MODULE_PACKAGES_ID) {
			for (int i = 0; i < list.size(); i++) w.putShort(pool.getPackageId(list.get(i)));
		} else {
			for (int i = 0; i < list.size(); i++) w.putShort(pool.getClassId(list.get(i)));
		}
	}

	public String toString() { return name()+": " + value; }
}