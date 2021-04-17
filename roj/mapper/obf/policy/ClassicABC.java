package roj.mapper.obf.policy;

import roj.collect.ToIntMap;
import roj.mapper.util.Desc;
import roj.text.CharList;

import java.util.Random;
import java.util.Set;

/**
 * Confusing chars
 *
 * @author Roj233
 * @since 2021/7/18 19:29
 */
public final class ClassicABC implements NamingFunction {
	public int i;
	ToIntMap<String> params = new ToIntMap<>();

	public ClassicABC() {}

	static final char[] ABC = new char[] {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', 'A', 'B', 'C', 'D', 'E',
										  'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'};

	private final CharList buf = new CharList();
	private boolean keepPackage;

	@Override
	public ClassicABC setKeepPackage(boolean keepPackage) {
		this.keepPackage = keepPackage;
		return this;
	}

	@Override
	public String obfClass(String origName, Set<String> noDuplicate, Random rand) {
		buf.clear();
		if (keepPackage) {
			int i = origName.lastIndexOf('/');
			if (i != -1) {
				String pkg = origName.substring(0, i);
				int v = params.getOrDefault(pkg, 0);
				params.putInt(pkg, v + 1);
				buf.append(origName, 0, i + 1);
				return getABC(v);
			}
		}

		return getABC(i++);
	}

	@Override
	public String obfName(Set<String> noDuplicate, Desc desc, Random rand) {
		if (noDuplicate.isEmpty() && !desc.param.contains(")")) params.clear();
		noDuplicate.add("");

		String param = desc.param;
		int v = params.increase(param, 1)-1;
		buf.clear();
		return getABC(v);
	}

	private String getABC(int i) {
		i = -i;
		while (i <= -ABC.length) {
			buf.append(ABC[-(i % ABC.length)]);
			i /= ABC.length;
		}
		return buf.append(ABC[-i]).toString();
	}
}
