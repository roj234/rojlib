package roj.asm.cp;

import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.text.CharList;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class CstNameAndType extends Constant {
	private CstUTF name, type;

	public CstNameAndType() {}

	public CstNameAndType(CstUTF nameIndex, CstUTF typeIndex) {
		this.name = nameIndex;
		this.type = typeIndex;
	}

	@Override
	public byte type() {
		return Constant.NAME_AND_TYPE;
	}

	@Override
	public final void write(DynByteBuf w) {
		w.put(Constant.NAME_AND_TYPE).putShort(name.getIndex()).putShort(type.getIndex());
	}

	public final String toString() {
		CharList sb = new CharList().append(super.toString())
			.append(" 引用[").append(name.getIndex()).append(",").append(type.getIndex()).append("] ");
		return parseNodeDesc(sb, null, name.str(), type.str());
	}
	static String parseNodeDesc(CharList sb, String owner, String name, String type) {
		if (owner != null) {
			name = owner.substring(owner.lastIndexOf('/')+1)+'.'+name;
		}

		if (type.startsWith("(")) {
			try {
				return sb.append(TypeHelper.humanize(Type.methodDesc(type), name, true)).toString();
			} catch (Exception ignored) {}
		} else {
			try {
				Type.fieldDesc(type).toString(sb);
				return sb.append(' ').append(name).toString();
			} catch (Exception ignored) {}
		}
		return sb.append("[解析失败] ").append(name).append('|').append(type).toString();
	}

	public final int hashCode() {
		return 31 * name.hashCode() + type.hashCode();
	}

	public final boolean equals(Object o) {
		return o instanceof CstNameAndType && equals0((CstNameAndType) o);
	}

	public final boolean equals0(CstNameAndType ref) {
		if (ref == this) return true;
		return ref.name.equals(name) && ref.type.equals(type);
	}

	public final CstUTF name() { return name; }
	public final void name(CstUTF name) {
		if (name == null) throw new NullPointerException("name");
		this.name = name;
	}

	public final CstUTF getType() { return type; }
	public final void setType(CstUTF type) {
		if (type == null) throw new NullPointerException("type");
		this.type = type;
	}

	@Override
	public final CstNameAndType clone() {
		CstNameAndType slf = (CstNameAndType) super.clone();
		slf.name = (CstUTF) name.clone();
		slf.type = (CstUTF) type.clone();
		return slf;
	}
}