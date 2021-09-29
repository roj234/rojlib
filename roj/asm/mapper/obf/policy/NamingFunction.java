package roj.asm.mapper.obf.policy;

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

    String obfName(Set<String> noDuplicate, String param, Random rand);

    default NamingFunction setKeepPackage(boolean keepPackage) {
        throw new UnsupportedOperationException(getClass().getName());
    }
}
