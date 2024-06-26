package roj.asm.cp;

import roj.asm.type.TypeHelper;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class CstClass extends CstRefUTF {
	public CstClass() {}
	public CstClass(String name) { super(new CstUTF(name)); }
	public CstClass(CstUTF v) { super(v); }

	@Override
	public byte type() { return Constant.CLASS; }
	@Override
	public String getEasyReadValue() {
		String str = name().str();
		return (str.startsWith("[")?TypeHelper.parseField(str).toString():str).replace('/', '.')+".class";
	}
	@Override
	public String getEasyCompareValue() { return name().str(); }
}