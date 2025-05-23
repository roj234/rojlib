package roj.plugins.obfuscator.naming;

import roj.asm.MemberDescriptor;
import roj.text.CharList;

import java.util.Random;
import java.util.Set;

/**
 * Simple not duplicate naming function
 *
 * @author Roj233
 * @since 2021/7/18 19:45
 */
public abstract class SimpleNamer implements NamingPolicy {
	private boolean keepPackage;
	private final CharList buf = new CharList();

	public int maxRetryAttempts = 10;
	private NamingPolicy fallback;

	public SimpleNamer setKeepPackage(boolean keepPackage) {
		this.keepPackage = keepPackage;
		return this;
	}

	public SimpleNamer setFallback(NamingPolicy fallback) {
		this.fallback = fallback;
		return this;
	}

	@Override
	public String obfClass(String name, Set<String> existNames, Random rnd) {
		buf.clear();

		int pos = -1;
		if (keepPackage) {
			pos = name.lastIndexOf('/');
			if (pos >= 0) pos++;
		}

		int i = maxRetryAttempts;
		while (true) {
			buf.clear();
			if (pos > 0) buf.append(name, 0, pos);
			if (!generateName(rnd, buf, 0)) return null;

			String nn = buf.toString();
			if (!existNames.contains(nn)) return nn;

			if (i-- == 0) {
				if (fallback == null) return null;
				return fallback.obfClass(name, existNames, rnd);
			}
		}
	}

	@Override
	public String obfName(Set<String> existNames, MemberDescriptor d, Random rnd) {
		int i = maxRetryAttempts;
		while (true) {
			buf.clear();
			if (!generateName(rnd, buf, d.rawDesc.startsWith("(")?1:2)) return null;

			String nn = buf.toString();
			if (existNames.add(nn.concat(d.rawDesc))) return nn;

			if (i-- == 0) {
				if (fallback == null) return null;
				return fallback.obfName(existNames, d, rnd);
			}
		}
	}

	protected abstract boolean generateName(Random rnd, CharList sb, int type);
}