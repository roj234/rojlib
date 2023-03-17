package roj.mapper.obf.policy;

import roj.mapper.util.Desc;

import java.util.Random;
import java.util.Set;

/**
 * Confusing chars
 *
 * @author Roj233
 * @since 2021/7/18 19:29
 */
public final class Deobfuscate extends SimpleNamer {
	public int a, b;

	public Deobfuscate() {}

	@Override
	public String obfClass(String origName, Set<String> noDuplicate, Random rand) {
		if (origName.length() - origName.lastIndexOf('/') > 4) return null;
		return obfClass0(origName, rand);
	}

	@Override
	public String obfName0(Random rand) {
		return buf.append("Klass_").append(String.valueOf(a++)).toString();
	}

	@Override
	public String obfName(Set<String> noDuplicate, Desc desc, Random rand) {
		return desc.param.charAt(0) == '(' ? ("method_" + a++) : ("field_" + b++);
	}
}
