package roj.asm.frame;

import roj.asm.tree.Clazz;
import roj.asm.tree.Method;
import roj.asm.util.AccessFlag;

/**
 * @author Roj234
 * @since 2022/2/5 15:36
 */
public class ClassPoet {
    public Clazz poem;
    public MethodPoet mp = new MethodPoet(this);

    public ClassPoet(Clazz clazz) {
        this.poem = clazz;
    }

    // check abstract...
    public MethodPoet method(String name, String desc, int flag) {
        Method m = new Method(AccessFlag.of(flag), poem, name, desc);
        poem.methods.add(m);
        mp.init(m);
        return mp;
    }
}
