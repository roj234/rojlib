package roj.asmx.mapper.util;

import roj.asm.tree.RawNode;
import roj.asm.type.Desc;

/**
 * @author Roj233
 * @since 2021/7/21 2:42
 */
public class NameAndType extends Desc {
	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null) return false;
		if (o instanceof RawNode d) return name.equals(d.name()) && param.equals(d.rawDesc());
		return false;
	}

	@Override
	public int hashCode() {return name.hashCode() * 31 + param.hashCode();}

	public NameAndType copy(String owner) {
		NameAndType nat = new NameAndType();
		nat.owner = owner;
		nat.name = name;
		nat.param = param;
		return nat;
	}
}