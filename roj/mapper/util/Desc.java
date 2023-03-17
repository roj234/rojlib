package roj.mapper.util;

import roj.asm.cst.ConstantPool;
import roj.asm.cst.CstNameAndType;
import roj.asm.cst.CstRef;
import roj.asm.tree.MoFNode;
import roj.asm.util.AccessFlag;
import roj.util.DynByteBuf;

/**
 * 对象描述符
 *
 * @author Roj233
 * @version 2.8
 * @since ?
 */
public class Desc implements MoFNode {
	public static final char UNSET = AccessFlag.PUBLIC | AccessFlag.PRIVATE;

	public String owner, name, param;
	public char flags;

	public Desc() {
		owner = name = param = "";
		flags = UNSET;
	}

	// As a Wildcard Field Descriptor
	public Desc(String owner, String name) {
		this.owner = owner;
		this.name = name;
		this.param = "";
		this.flags = UNSET;
	}

	public Desc(String owner, String name, String param) {
		this.owner = owner;
		this.name = name;
		this.param = param;
		this.flags = UNSET;
	}

	public Desc(String owner, String name, String param, int flags) {
		this.owner = owner;
		this.name = name;
		this.param = param;
		this.flags = (char) flags;
	}

	@Override
	public String toString() {
		return "{" + owner + '.' + name + (param.isEmpty()?"":' ' + param) + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Desc)) return false;
		Desc other = (Desc) o;
		return this.param.equals(other.param) && other.owner.equals(this.owner) && other.name.equals(this.name);
	}

	@Override
	public int hashCode() {
		return owner.hashCode() ^ param.hashCode() ^ name.hashCode();
	}

	public final Desc read(CstRef ref) {
		this.owner = ref.getClassName();
		CstNameAndType a = ref.desc();
		this.name = a.getName().getString();
		this.param = a.getType().getString();
		return this;
	}

	public final Desc copy() {
		return new Desc(owner, name, param, flags);
	}

	@Override
	public final void toByteArray(DynByteBuf w, ConstantPool pool) {
		throw new UnsupportedOperationException();
	}

	@Override
	public final String name() {
		return name;
	}

	@Override
	public final String rawDesc() {
		return param;
	}

	@Override
	public void accessFlag(int flag) {
		this.flags = (char) flag;
	}

	@Override
	public final char accessFlag() {
		return flags;
	}

	@Override
	public final int type() {
		return 5;
	}
}