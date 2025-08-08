package roj.plugins.obfuscator.naming;

import roj.asm.MemberDescriptor;
import roj.text.CharList;

import java.util.Random;
import java.util.Set;

/**
 * @author Roj233
 * @since 2021/7/18 19:29
 */
public final class Deobfuscate implements NamingPolicy {
	public int a, b;
	CharList buf = new CharList();
	boolean keep = true;

	public Deobfuscate() {}

	@Override
	public NamingPolicy setKeepPackage(boolean keepPackage) { keep = keepPackage; return this; }

	@Override
	public String obfClass(String name, Set<String> existNames, Random rnd) {
		int pos = keep ? name.lastIndexOf('/') : -1;
		if (pos >= 0) pos++;
		else pos = 0;

		buf.clear();
		return buf.append(name, 0, pos).append("class_").append(a++).append('_').toString();
	}

	@Override
	public String obfName(Set<String> existNames, MemberDescriptor d, Random rnd) {
		if (d.name.equals("main")) return null;

		return (d.rawDesc.charAt(0) == '(' ? ("method_" + a++) : ("field_" + b++)) + "_";
	}
}