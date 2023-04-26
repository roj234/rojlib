package roj.asm.tree;

import roj.asm.cst.ConstantPool;
import roj.asm.cst.CstUTF;
import roj.asm.tree.attr.Attribute;
import roj.asm.util.AttributeList;
import roj.util.DynByteBuf;

import java.util.Objects;

/**
 * 简单组件基类
 *
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public abstract class RawNode implements MoFNode {
	RawNode(int access, CstUTF name, CstUTF type) {
		this.access = (char) access;
		this.name = name;
		this.type = type;
	}

	public CstUTF name, type;
	public char access;

	public final void toByteArray(DynByteBuf w, ConstantPool pool) {
		w.putShort(access).putShort(pool.reset(name).getIndex()).putShort(pool.reset(type).getIndex());
		if (attributes == null) {
			w.putShort(0);
			return;
		}

		attributes.toByteArray(w, pool);
	}

	public final String name() { return name.str(); }
	public final void name(ConstantPool cp, String name) { this.name = Objects.requireNonNull(cp,"cp").getUtf(name); }

	public final String rawDesc() { return type.str(); }
	public final void rawDesc(ConstantPool cp, String rawDesc) { type = Objects.requireNonNull(cp,"cp").getUtf(rawDesc); }

	public final void modifier(int flag) { access = (char) flag; }
	public final char modifier() { return access; }

	AttributeList attributes;

	public final Attribute attrByName(String name) { return attributes == null ? null : (Attribute) attributes.getByName(name); }
	public final AttributeList attributes() { return attributes == null ? attributes = new AttributeList() : attributes; }
	public final AttributeList attributesNullable() { return attributes; }
}
