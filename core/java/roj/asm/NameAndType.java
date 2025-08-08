package roj.asm;

/**
 * @author Roj233
 * @since 2021/7/21 2:42
 */
public class NameAndType extends MemberDescriptor {
	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null) return false;
		if (o instanceof Member d) return name.equals(d.name()) && rawDesc.equals(d.rawDesc());
		return false;
	}

	@Override
	public int hashCode() {return name.hashCode() * 31 + rawDesc.hashCode();}

	public NameAndType copy(String owner) {
		NameAndType nat = new NameAndType();
		nat.owner = owner;
		nat.name = name;
		nat.rawDesc = rawDesc;
		return nat;
	}
}