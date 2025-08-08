package roj.plugins.obfuscator.naming;

import roj.asm.MemberDescriptor;

import java.util.Random;
import java.util.Set;

/**
 * @author Roj233
 * @since 2021/7/18 19:39
 */
public interface NamingPolicy {
	String obfClass(String name, Set<String> noDuplicate, Random rand);
	String obfName(Set<String> noDuplicate, MemberDescriptor d, Random rand);
	default NamingPolicy setKeepPackage(boolean keepPackage) {
		throw new UnsupportedOperationException(getClass().getName());
	}
}