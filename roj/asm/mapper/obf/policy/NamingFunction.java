package roj.asm.mapper.obf.policy;

import roj.asm.mapper.util.Desc;

import java.util.Random;
import java.util.Set;

/**
 * NamingFunction
 *
 * @author Roj233
 * @since 2021/7/18 19:39
 */
public interface NamingFunction {
    String obfClass(String origName, Set<String> noDuplicate, Random rand);

    String obfName(Set<String> noDuplicate, Desc param, Random rand);

    default NamingFunction setKeepPackage(boolean keepPackage) {
        throw new UnsupportedOperationException(getClass().getName());
    }
}
