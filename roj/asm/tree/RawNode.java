package roj.asm.tree;

import roj.asm.cst.ConstantPool;
import roj.asm.cst.CstUTF;
import roj.asm.tree.attr.Attribute;
import roj.asm.util.AttributeList;
import roj.util.DynByteBuf;

import javax.annotation.Nullable;

/**
 * 简单组件基类
 *
 * @author Roj234
 * @version 1.1
 * @since 2021/5/29 17:16
 */
public abstract class RawNode implements MoFNode {
	RawNode(int accesses, CstUTF name, CstUTF type) {
		this.accesses = (char) accesses;
		this.name = name;
		this.type = type;
	}

	public CstUTF name, type;
	public char accesses;

	@Override
	public String name() {
		return name.getString();
	}

	@Override
	public void name(ConstantPool cp, String name) {
		if (cp == null) throw new UnsupportedOperationException();
		this.name = cp.getUtf(name);
	}

	@Override
	public String rawDesc() {
		return type.getString();
	}

	@Override
	public void rawDesc(ConstantPool cp, String rawDesc) {
		if (cp == null) throw new UnsupportedOperationException();
		this.type = cp.getUtf(rawDesc);
	}

	@Override
	public void accessFlag(int flag) {
		this.accesses = (char) flag;
	}

	@Override
	public char accessFlag() {
		return accesses;
	}

	private AttributeList attributes;

	public void toByteArray(DynByteBuf w, ConstantPool pool) {
		w.putShort(accesses).putShort(pool.reset(name).getIndex()).putShort(pool.reset(type).getIndex());
		if (attributes == null) {
			w.putShort(0);
			return;
		}

		attributes.toByteArray(w, pool);
	}

	public Attribute attrByName(String name) {
		return attributes == null ? null : (Attribute) attributes.getByName(name);
	}

	@Override
	public AttributeList attributes() {
		return attributes == null ? attributes = new AttributeList() : attributes;
	}

	@Nullable
	@Override
	public AttributeList attributesNullable() {
		return attributes;
	}
}
