package roj.asm.remapper.struct;

import roj.asm.cst.CstUTF;
import roj.asm.util.ConstantPool;
import roj.asm.util.IType;
import roj.asm.util.type.ParamHelper;
import roj.asm.util.type.SignatureHelper;
import roj.util.ByteList;
import roj.util.ByteReader;

import java.util.ArrayList;
import java.util.List;

public final class AttrLVT_Simple {
    public AttrLVT_Simple(ConstantPool pool, ByteReader r, boolean generic) {
        this.list = readVar(pool, r, generic);
    }

    private static List<V> readVar(ConstantPool pool, ByteReader r, boolean generic) {
        final int len = r.readUnsignedShort();
        List<V> list = new ArrayList<>(len);

        for (int i = 0; i < len; i++) {
            r.index += 4;
            int clo = r.index;
            CstUTF name = ((CstUTF) pool.get(r));
            CstUTF desc = ((CstUTF) pool.get(r));
            IType type = generic ? SignatureHelper.parse(desc.getString()) : ParamHelper.parseField(desc.getString());
            V sv = new V();
            sv.nameId = clo;
            sv.name = name;
            sv.refType = desc;
            sv.type = type;
            sv.bl = r.getBytes();
            sv.slot = r.readUnsignedShort();
            list.add(sv);
        }
        return list;
    }

    public final List<V> list;

    public static class V {
        public CstUTF name;
        public CstUTF refType;
        public IType type;
        public ByteList bl;
        public int slot, nameId;
    }
}