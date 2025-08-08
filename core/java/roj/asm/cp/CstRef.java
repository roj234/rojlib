package roj.asm.cp;

import roj.text.CharList;
import roj.util.DynByteBuf;

import java.util.Objects;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public abstract sealed class CstRef extends Constant permits CstRef.Field, CstRef.Method, CstRef.Interface {
	private CstClass clazz;
	private CstNameAndType desc;

	CstRef(CstClass c, CstNameAndType d) {
		this.clazz = c;
		this.desc = d;
	}

	@Override
	public final void write(DynByteBuf w) {
		w.put(type()).putShort(clazz.index).putShort(desc.index);
	}

	public final String toString() {
		CharList sb = new CharList().append(super.toString())
			.append(" 引用[").append((int) clazz.index).append(",").append((int) desc.index).append("] ");
		return CstNameAndType.parseNodeDesc(sb, clazz.value().str(), desc.name().str(), desc.rawDesc().str());
	}

	public final String owner() { return clazz.value().str(); }
	public final String name() { return desc.name().str(); }
	public final String rawDesc() { return desc.rawDesc().str(); }

	public CstClass clazz() {return clazz;}
	public CstNameAndType nameAndType() {return desc;}

	public final void clazz(CstClass clazz) {this.clazz = Objects.requireNonNull(clazz);}
	public final void nameAndType(CstNameAndType desc) {this.desc = Objects.requireNonNull(desc);}

	public final boolean equals(Object o) {
		if (o == this) return true;
		if (o == null || o.getClass() != getClass()) return false;
		CstRef x = (CstRef) o;
		return x.clazz.value().equals(clazz.value()) && x.desc.equals0(desc);
	}
	public final int hashCode() {return 31 * (31 * desc.hashCode() + clazz.value().hashCode()) + type();}

	@Override
	public final CstRef clone() {
		CstRef slf = (CstRef) super.clone();
		slf.clazz = (CstClass) clazz.clone();
		slf.desc = desc.clone();
		return slf;
	}

	public static final class Field extends CstRef {
		Field(CstClass c, CstNameAndType d) {super(c, d);}
		public byte type() {return Constant.FIELD;}
	}
	public static final class Method extends CstRef {
		Method(CstClass c, CstNameAndType d) {super(c, d);}
		public byte type() {return Constant.METHOD;}
	}
	public static final class Interface extends CstRef {
		Interface(CstClass c, CstNameAndType d) {super(c, d);}
		public byte type() {return Constant.INTERFACE;}
	}
}