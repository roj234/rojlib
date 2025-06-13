package roj.asm;

import roj.asm.cp.CstRef;

/**
 * 成员描述符
 * @author Roj233
 */
public class MemberDescriptor implements Member {
	public static final char FLAG_UNSET = Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE;

	public String owner, name, rawDesc;
	public transient char modifier;

	public MemberDescriptor() {
		owner = name = rawDesc = "";
		modifier = FLAG_UNSET;
	}

	/**
	 * Wildcard field descriptor (for {@link roj.asmx.mapper.Mapper})
	 */
	public MemberDescriptor(String owner, String name) {
		this.owner = owner;
		this.name = name;
		this.rawDesc = "";
		this.modifier = FLAG_UNSET;
	}

	public MemberDescriptor(String owner, String name, String rawDesc) {
		this.owner = owner;
		this.name = name;
		this.rawDesc = rawDesc;
		this.modifier = FLAG_UNSET;
	}

	public MemberDescriptor(String owner, String name, String rawDesc, int modifier) {
		this.owner = owner;
		this.name = name;
		this.rawDesc = rawDesc;
		this.modifier = (char) modifier;
	}

	public static MemberDescriptor fromJavapLike(String jpdesc) {
		String owner, name, rawDesc;

		int klass = jpdesc.lastIndexOf('.');
		int pvrvm = jpdesc.indexOf('(');
		if (pvrvm < 0) pvrvm = jpdesc.indexOf(' ');
		if (pvrvm < 0) throw new IllegalStateException("Invalid javap desc: "+jpdesc);

		owner = klass > 0 ? jpdesc.substring(0, klass).replace('.', '/') : "";
		name = jpdesc.substring(klass+1, pvrvm);
		rawDesc = jpdesc.substring(jpdesc.charAt(pvrvm)==' '?pvrvm+1:pvrvm);

		return new MemberDescriptor(owner, name, rawDesc);
	}

	@Override
	public String toString() { return owner + '.' + name + (rawDesc.isEmpty()?"":(rawDesc.startsWith("(")?"":" ") + rawDesc); }

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof MemberDescriptor other)) return false;
		return this.rawDesc.equals(other.rawDesc) && other.owner.equals(this.owner) && other.name.equals(this.name);
	}

	@Override
	public int hashCode() { return owner.hashCode() ^ rawDesc.hashCode() ^ name.hashCode(); }

	public final MemberDescriptor read(CstRef ref) {
		this.owner = ref.owner();
		this.name = ref.name();
		this.rawDesc = ref.rawDesc();
		return this;
	}

	public final MemberDescriptor copy() { return new MemberDescriptor(owner, name, rawDesc, modifier); }

	@Override public final String owner() {return owner;}
	@Override public final String name() { return name; }
	@Override public final String rawDesc() { return rawDesc; }
	@Override public final char modifier() { return modifier; }
	@Override public void modifier(int flag) { this.modifier = (char) flag; }
}