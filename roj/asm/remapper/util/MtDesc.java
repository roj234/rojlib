package roj.asm.remapper.util;


import roj.asm.cst.CstNameAndType;
import roj.asm.cst.CstRef;
import roj.asm.util.FlagList;

public class MtDesc {
    public String owner, name, param;
    public FlagList flags;

    public MtDesc(String owner, String name, String param) {
        this.owner = owner;
        this.name = name;
        this.param = param;
    }

    public MtDesc(String owner, String name, String param, FlagList flags) {
        this.owner = owner;
        this.name = name;
        this.param = param;
        this.flags = flags;
    }

    @Override
    public String toString() {
        return "MD{" + owner + '.' + name + ' ' + param + ", flags=" + flags + '}';
    }

    @Override
    public boolean equals(Object o) {
        if(!(o instanceof MtDesc))
            return false;
        MtDesc other = (MtDesc)o;
        return this.param.equals(other.param) && other.owner.equals(this.owner) && other.name.equals(this.name);
    }

    @Override
    public int hashCode() {
        return owner.hashCode() ^ param.hashCode() ^ name.hashCode();
    }

    public MtDesc read(CstRef ref) {
        this.owner = ref.getClassName();
        CstNameAndType a = ref.desc();
        this.name = a.getName().getString();
        this.param = a.getType().getString();
        return this;
    }

    public MtDesc copy() {
        return new MtDesc(owner, name, param, flags);
    }
}