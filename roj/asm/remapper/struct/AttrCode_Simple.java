package roj.asm.remapper.struct;

import roj.asm.cst.CstUTF;
import roj.asm.util.ConstantPool;
import roj.util.ByteReader;

public class AttrCode_Simple {
    public AttrCode_Simple(ByteReader list, ConstantPool pool) {
        initialize(pool, list);
    }

    public AttrLVT_Simple lvt, lvtt;

    public void initialize(ConstantPool pool, ByteReader r) {
        r.index += 4; // stack size
        int bl = r.readInt();
        r.index += bl; // code

        int len = r.readUnsignedShort(); // exception
        r.index += 8 * len;

        len = r.readUnsignedShort();
        for (int i = 0; i < len; i++) {
            String name = ((CstUTF) pool.get(r)).getString();
            int end = r.readInt() + r.index;
            switch (name) {
                case "LocalVariableTable":
                    lvt = new AttrLVT_Simple(pool, r, false);
                    break;
                case "LocalVariableTypeTable":
                    lvtt = new AttrLVT_Simple(pool, r, true);
                    break;
            }
            r.index = end;
        }
    }
}