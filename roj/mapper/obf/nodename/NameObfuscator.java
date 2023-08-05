package roj.mapper.obf.nodename;

import roj.mapper.util.Desc;

import java.util.Random;
import java.util.Set;

/**
 * @author Roj233
 * @since 2021/7/18 19:39
 */
public interface NameObfuscator {
	String obfClass(String name, Set<String> noDuplicate, Random rand);
	String obfName(Set<String> noDuplicate, Desc d, Random rand);
	default NameObfuscator setKeepPackage(boolean keepPackage) {
		throw new UnsupportedOperationException(getClass().getName());
	}
}
