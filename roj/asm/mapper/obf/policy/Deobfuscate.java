package roj.asm.mapper.obf.policy;

import roj.asm.mapper.util.Desc;

import java.util.Random;
import java.util.Set;

/**
 * Confusing chars
 *
 * @author Roj233
 * @since 2021/7/18 19:29
 */
public final class Deobfuscate extends SimpleNamer {
    public int a,b;

    public Deobfuscate() {}

    @Override
    public String obfName0(Random rand) {
        return "Klass_" + a++;
    }

    @Override
    public String obfName(Set<String> noDuplicate, Desc desc, Random rand) {
        return desc.param.charAt(0) == '(' ? ("method_" + a++) : ("field_" + b++);
    }
}
