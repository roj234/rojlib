package roj.asm.tree.attr;

import roj.asm.cst.ConstantPool;
import roj.asm.cst.CstUTF;
import roj.collect.SimpleList;
import roj.util.DynByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class AttrStringList extends Attribute {
	public static final String EXCEPTIONS = "Exceptions";
	public static final String NEST_MEMBERS = "NestMembers";
	public static final String PERMITTED_SUBCLASSES = "PermittedSubclasses";

	public final byte type;

	public AttrStringList(String name, int type) {
		super(name);
		classes = new ArrayList<>();
		this.type = (byte) type;
	}

	/**
	 * @param type true: {@link roj.asm.tree.Method} false: {@link AttrCode}
	 */
	public AttrStringList(String name, DynByteBuf r, ConstantPool pool, int type) {
		super(name);
		this.type = (byte) type;

		int len = r.readUnsignedShort();
		classes = new SimpleList<>(len);

		switch (type) {
			case 0:
				while (len-- > 0) {
					classes.add(pool.getName(r));
				}
				break;
			case 1:
				while (len-- > 0) {
					classes.add(((CstUTF) pool.get(r)).str());
				}
				break;
		}
	}

	public final List<String> classes;

	@Override
	public boolean isEmpty() {
		return classes.isEmpty();
	}

	@Override
	protected void toByteArray1(DynByteBuf w, ConstantPool pool) {
		final List<String> ex = this.classes;

		w.putShort(ex.size());
		int i = 0;
		switch (type) {
			case 0:
				for (; i < ex.size(); i++) {
					w.putShort(pool.getClassId(ex.get(i)));
				}
				break;
			case 1:
				for (; i < ex.size(); i++) {
					w.putShort(pool.getUtfId(ex.get(i)));
				}
				break;
		}
	}

	public String toString() {
		return name + ": " + classes;
	}
}