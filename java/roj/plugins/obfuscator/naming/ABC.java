package roj.plugins.obfuscator.naming;

import roj.asm.MemberDescriptor;
import roj.collect.ToIntMap;
import roj.text.CharList;

import java.util.Random;
import java.util.Set;

/**
 * @author Roj233
 * @since 2021/7/18 19:29
 */
public class ABC implements NamingPolicy {
	private int npClassId;
	private final ToIntMap<String> counter = new ToIntMap<>();
	private final char[] chars;

	public ABC() { this(ABC); }
	public ABC(char[] c) { chars = c; }

	public static final char[] ABC = new char[] {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', 'A', 'B', 'C', 'D', 'E',
										  'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'};

	private final CharList buf = new CharList();
	private boolean keepPackage;

	@Override
	public ABC setKeepPackage(boolean keepPackage) {
		this.keepPackage = keepPackage;
		return this;
	}

	@Override
	public String obfClass(String name, Set<String> noDuplicate, Random rand) {
		buf.clear();
		if (keepPackage) {
			int i = name.lastIndexOf('/');
			if (i != -1) {
				String pkg = name.substring(0, i);
				int v = counter.getOrDefault(pkg, 0);
				counter.putInt(pkg, v + 1);
				buf.append(name, 0, i + 1);
				return num2str(v);
			}
		}

		return num2str(npClassId++);
	}

	@Override
	public String obfName(Set<String> noDuplicate, MemberDescriptor d, Random rand) {
		if (noDuplicate.isEmpty() && !d.rawDesc.contains(")")) counter.clear();
		noDuplicate.add("");

		String param = d.rawDesc;
		int v = counter.increment(param, 1)-1;
		buf.clear();
		return num2str(v);
	}

	protected String num2str(int i) {
		i = -i;
		while (i <= -chars.length) {
			buf.append(chars[-(i % chars.length)]);
			i /= chars.length;
		}
		return buf.append(chars[-i]).toString();
	}
}