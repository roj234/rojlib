package roj.asm.tree.attr;

import roj.asm.cp.ConstantPool;
import roj.collect.SimpleList;
import roj.util.DynByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class AttrClassList extends Attribute {
	public static final String
		EXCEPTIONS = "Exceptions",
		NEST_MEMBERS = "NestMembers",
		PERMITTED_SUBCLASSES = "PermittedSubclasses",
		MODULE_PACKAGES = "ModulePackages";
	private static final int MODULE_PACKAGES_ID = NAMED_ID.getInt(MODULE_PACKAGES);

	private final byte name;

	public AttrClassList(String name) {
		this.name = (byte) NAMED_ID.getInt(name);
		value = new ArrayList<>();
	}

	public AttrClassList(String name, DynByteBuf r, ConstantPool pool) {
		this.name = (byte) NAMED_ID.getInt(name);

		int len = r.readUnsignedShort();
		value = new SimpleList<>(len);
		while (len-- > 0) value.add(pool.getRefName(r));
	}

	public final List<String> value;

	@Override
	public boolean isEmpty() { return value.isEmpty(); }
	@Override
	public String name() { return NAMED_ID.get(name); }
	@Override
	protected void toByteArray1(DynByteBuf w, ConstantPool pool) {
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