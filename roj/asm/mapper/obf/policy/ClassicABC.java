package roj.asm.mapper.obf.policy;

import roj.asm.mapper.util.Desc;
import roj.collect.ToIntMap;
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

    static final char[] ABC = new char[] {
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'
    };

    private final CharList buf = new CharList();
    private       boolean  keepPackage;

    @Override
    public ClassicABC setKeepPackage(boolean keepPackage) {
        this.keepPackage = keepPackage;
        return this;
    }

    @Override
    public String obfClass(String origName, Set<String> noDuplicate, Random rand) {
        params.clear();
        buf.clear();
        if (keepPackage) {
            int i = origName.lastIndexOf('/');
            if(i != -1) {
                buf.append(origName, 0, i + 1);
            }
        }

        return getABC(++i);
    }

    private int ld = 0;

    @Override
    public String obfName(Set<String> noDuplicate, Desc desc, Random rand) {
        if (noDuplicate.size() < ld)
            params.clear();
        ld = noDuplicate.size();

        String param = desc.param;
        int v = params.getOrDefault(param, 1);
        params.putInt(param, v + 1);
        buf.clear();
        return getABC(v);
    }

    private String getABC(int i) {
        while (i != 0) {
            buf.append(ABC[i % ABC.length]);
            i /= ABC.length;
        }
        return buf.toString();
    }
}
