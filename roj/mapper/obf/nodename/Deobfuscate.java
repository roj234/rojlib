package roj.mapper.obf.nodename;

import roj.mapper.util.Desc;
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

	public Deobfuscate() {}

	@Override
	public String obfClass(String name, Set<String> existNames, Random rnd) {
		int pos = name.lastIndexOf('/');
		if (pos >= 0) pos++;

		buf.clear();
		return buf.append(name, 0, pos).append("class_").append(a++).append('_').toString();
	}

	@Override
	public String obfName(Set<String> existNames, Desc d, Random rnd) {
		if (d.name.equals("main")) return null;

		return (d.param.charAt(0) == '(' ? ("method_" + a++) : ("field_" + b++)) + "_";
	}
}