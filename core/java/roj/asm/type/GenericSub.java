package roj.asm.type;

import roj.text.CharList;

import java.util.List;

/**
 * @author Roj234
 * @since 2022/11/1 11:21
 */
public class GenericSub extends IGeneric {
	public GenericSub(String name) {this.owner = name;}
	public GenericSub(String owner, List<IType> children) {this.owner = owner;this.typeParameters = children;}

	@Override public byte kind() {return PARAMETERIZED_CHILD;}

	public void toDesc(CharList sb) {
		sb.append('.').append(owner);
		if (!typeParameters.isEmpty()) {
			sb.append('<');
			for (int i = 0; i < typeParameters.size(); i++) {
				typeParameters.get(i).toDesc(sb);
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

		if (!typeParameters.isEmpty()) {
			sb.append('<');
			int i = 0;
			while (true) {
				typeParameters.get(i++).toString(sb);
				if (i == typeParameters.size()) break;
				sb.append(", ");
			}
			sb.append('>');
		}
		if (sub != null) sub.toString(sb.append('.'));
	}

	@Override public void validate(int positionType, int index) {throw new IllegalStateException("<泛型子类>"+this+" 不允许作为泛型TopLevel序列化");}
}