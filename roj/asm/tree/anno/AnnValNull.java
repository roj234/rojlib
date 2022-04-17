package roj.asm.tree.anno;

import roj.asm.util.ConstantPool;
import roj.util.ByteList;

/**
 * @author solo6975
 * @since 2022/4/30 19:35
 */
public final class AnnValNull extends AnnVal {
    public static final AnnValNull NULL = new AnnValNull();

    @Override
    public byte type() {
        return 0;
    }

    @Override
    public void toByteArray(ConstantPool pool, ByteList w) {
        throw new UnsupportedOperationException("Null cannot be serialized");
    }

    @Override
    public String toString() {
        return "null";
    }
}
