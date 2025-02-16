package roj.asm.cp;

import roj.asm.type.Type;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class CstClass extends CstRefUTF {
	public CstClass(CstUTF v) { super(v); }
	public CstClass(String name) { super(name); }
	public CstClass() {}

	@Override public byte type() {return Constant.CLASS;}
	@Override public String toString() {
		String str = name().str();
		return (str.startsWith("[")?Type.fieldDesc(str).toString():str).replace('/', '.')+".class";
	}
	@Override public String getEasyCompareValue() { return name().str(); }
}