package roj.asm.cp;

import roj.text.Tokenizer;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class CstString extends CstRefUTF {
	public CstString(CstUTF v) { super(v); }
	public CstString(String name) { super(name); }

	@Override public byte type() { return STRING; }
	@Override public String toString() { return '"'+Tokenizer.escape(value().str())+'"'; }
	@Override public String getEasyCompareValue() { return value().str(); }
}