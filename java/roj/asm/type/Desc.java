package roj.asm.type;

import roj.asm.Opcodes;
import roj.asm.cp.CstRef;
import roj.asm.tree.RawNode;

/**
 * 对象描述符
 *
 * @author Roj233
 */
public class Desc implements RawNode {
	public static final char FLAG_UNSET = Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE;

	public String owner, name, param;
	public char flags;

	public Desc() {
		owner = name = param = "";
		flags = FLAG_UNSET;
	}

	// As a Wildcard Field Descriptor
	public Desc(String owner, String name) {
		this.owner = owner;
		this.name = name;
		this.param = "";
		this.flags = FLAG_UNSET;
	}

	public Desc(String owner, String name, String param) {
		this.owner = owner;
		this.name = name;
		this.param = param;
		this.flags = FLAG_UNSET;
	}

	public Desc(String owner, String name, String param, int flags) {
		this.owner = owner;
		this.name = name;
		this.param = param;
		this.flags = (char) flags;
	}

	public static Desc fromJavapLike(String jpdesc) {
		String owner, name, param;

		int klass = jpdesc.lastIndexOf('.');
		int pvrvm = jpdesc.indexOf('(');
		if (pvrvm < 0) pvrvm = jpdesc.indexOf(' ');
		if (pvrvm < 0) throw new IllegalStateException("Invalid javap desc: "+jpdesc);

		owner = klass > 0 ? jpdesc.substring(0, klass).replace('.', '/') : "";
		name = jpdesc.substring(klass+1, pvrvm);
		param = jpdesc.substring(jpdesc.charAt(pvrvm)==' '?pvrvm+1:pvrvm);

		return new Desc(owner, name, param);
	}

	@Override
	public String toString() { return owner + '.' + name + (param.isEmpty()?"":(param.startsWith("(")?"":" ") + param); }

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Desc)) return false;
		Desc other = (Desc) o;
		return this.param.equals(other.param) && other.owner.equals(this.owner) && other.name.equals(this.name);
	}

	@Override
	public int hashCode() { return owner.hashCode() ^ param.hashCode() ^ name.hashCode(); }

	public final Desc read(CstRef ref) {
		this.owner = ref.className();
		this.name = ref.descName();
		this.param = ref.descType();
		return this;
	}

	public final Desc copy() { return new Desc(owner, name, param, flags); }

	public static boolean paramEqual(String a, String b) {
		if (a.equals(b)) return true;
		if (!a.startsWith("(") || !b.startsWith("(")) return false;
		int i = a.lastIndexOf(')');
		return i == b.lastIndexOf(')') && a.regionMatches(0, b, 0, i);
	}

	@Override
	public final String name() { return name; }
	@Override
	public final String rawDesc() { return param; }
	@Override
	public void modifier(int flag) { this.flags = (char) flag; }
	@Override
	public final char modifier() { return flags; }
}