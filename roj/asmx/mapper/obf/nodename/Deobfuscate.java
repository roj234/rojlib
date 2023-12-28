package roj.asmx.mapper.obf.nodename;

import roj.asm.type.Desc;
import roj.text.CharList;

import java.util.Random;
import java.util.Set;

/**
 * @author Roj233
 * @since 2021/7/18 19:29
 */
public final class Deobfuscate implements NameObfuscator {
	public int a, b;
	CharList buf = new CharList();
	boolean keep = true;

	public Deobfuscate() {}

	@Override
	public NameObfuscator setKeepPackage(boolean keepPackage) { keep = keepPackage; return this; }

	@Override
	public String obfClass(String name, Set<String> existNames, Random rnd) {
		int pos = keep ? name.lastIndexOf('/') : -1;
		if (pos >= 0) pos++;
		else pos = 0;

		buf.clear();
		return buf.append(name, 0, pos).append("class_").append(a++).append('_').toString();
	}

	@Override
	public String obfName(Set<String> existNames, Desc d, Random rnd) {
		if (d.name.equals("main")) return null;

		return (d.param.charAt(0) == '(' ? ("method_" + a++) : ("field_" + b++)) + "_";
	}
}