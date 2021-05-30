package roj.asm.remapper.util;

import roj.asm.cst.CstRef;
import roj.asm.util.FlagList;

public final class FlDesc {
    public String owner, name;
    public FlagList flags;

    public FlDesc(String owner, String name) {
        this.owner = owner;
        this.name = name;
    }

    public FlDesc(String owner, String name, FlagList flags) {
        this.owner = owner;
        this.name = name;
        this.flags = flags;
    }

    public boolean equals(Object o) {
        if(!(o instanceof FlDesc))
            return false;
        FlDesc other = (FlDesc)o;
        return this.name.equals(other.name) && other.owner.equals(this.owner);
    }

    @Override
    public int hashCode() {
        return owner.hashCode() ^ name.hashCode();
    }

    @Override
    public String toString() {
        return "FD{" + owner + '.' + name + '}';
    }

    public FlDesc read(CstRef ref) {
        this.owner = ref.getClassName();
        this.name = ref.desc().getName().getString();
        return this;
    }

    public FlDesc copy() {
        return new FlDesc(owner, name, flags);
    }
}