package roj.asm.mapper.obf.policy;

import roj.asm.mapper.util.Desc;
import roj.text.CharList;

import java.util.Random;
import java.util.Set;

/**
 * Simple not duplicate naming function
 * @author Roj233
 * @since 2021/7/18 19:45
 */
public abstract class SimpleNamer implements NamingFunction {
    boolean keepPackage;
    public SimpleNamer setKeepPackage(boolean keepPackage) {
        this.keepPackage = keepPackage;
        return this;
    }

    CharList buf = new CharList();
    protected String obfClass0(String origName, Random rand) {
        buf.clear();
        if (keepPackage) {
            int i = origName.lastIndexOf('/');
            if(i != -1) {
                buf.append(origName, 0, i + 1);
            }
        }

        return obfName0(rand);
    }
    protected abstract String obfName0(Random rand);

    protected int maxRetryAttempts = 10;
    protected NamingFunction fallback;
    public SimpleNamer setFallback(NamingFunction fallback) {
        this.fallback = fallback;
        return this;
    }

    @Override
    public final String obfClass(String origName, Set<String> noDuplicate, Random rand) {
        int i = maxRetryAttempts;
        String s;
        while (!noDuplicate.add(s = obfClass0(origName, rand)))
            if(i-- == 0) {
                if(fallback == null) {
                    throw new IllegalArgumentException("Unable to find a not duplicate name after " + maxRetryAttempts + " retry attempts for " + origName);
                } else {
                    return fallback.obfClass(origName, noDuplicate, rand);
                }
            }
        return s;
    }

    @Override
    public String obfName(Set<String> noDuplicate, Desc param, Random rand) {
        int i = maxRetryAttempts;
        String s;
        while (!noDuplicate.add((s = obfName0(rand)) + param))
            if(i-- == 0) {
                if(fallback == null) {
                    throw new IllegalArgumentException("Unable to find a not duplicate name after " + maxRetryAttempts + " retry attempts for " + param);
                } else {
                    return fallback.obfName(noDuplicate, param, rand);
                }
            }
        return s;
    }
}
