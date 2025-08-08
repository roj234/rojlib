package roj.asm.type;

import roj.text.CharList;

import java.util.List;

/**
 * @author Roj234
 * @since 2022/11/1 11:21
 */
public class GenericSub extends IGeneric {
	public GenericSub(String name) {this.owner = name;}
	public GenericSub(String owner, List<IType> children) {this.owner = owner;this.children = children;}

	@Override public byte genericType() {return GENERIC_SUBCLASS_TYPE;}

	public void toDesc(CharList sb) {
		sb.append('.').append(owner);
		if (!children.isEmpty()) {
			sb.append('<');
			for (int i = 0; i < children.size(); i++) {
				children.get(i).toDesc(sb);
			}
			sb.append('>');
		}
		if (sub != null) {
			sub.toDesc(sb);
		} else {
			sb.append(';');
		}
	}

	public void toString(CharList sb) {
		sb.append(owner);

		if (!children.isEmpty()) {
			sb.append('<');
			int i = 0;
			while (true) {
				children.get(i++).toString(sb);
				if (i == children.size()) break;
				sb.append(", ");
			}
			sb.append('>');
		}
		if (sub != null) sub.toString(sb.append('.'));
	}

	@Override public void validate(int position, int index) {throw new IllegalStateException("<泛型子类>"+this+" 不允许作为泛型TopLevel序列化");}
}