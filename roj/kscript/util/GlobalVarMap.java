package roj.kscript.util;

import roj.collect.MyHashMap;
import roj.kscript.type.KType;
import roj.kscript.util.opm.GOEntry;
import roj.kscript.util.opm.KOEntry;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/4/26 23:16
 */
public class GlobalVarMap extends MyHashMap<String, KType> {
    public GlobalVarMap() {
        super(8);
    }

    public GlobalVarMap(MyHashMap<String, KType> vars) {
        super(vars);
    }

    @Override
    protected MyHashMap.Entry<String, KType> createEntry(String id) {
        return new GOEntry(id, null);
    }

    public void reset() {
        final Entry<?, ?>[] entries = this.entries;
        if (entries == null)
            return;
        for (int i = 0; i < length; i++) {
            GOEntry entry = (GOEntry) entries[i];
            if (entry == null)
                continue;
            while (entry != null) {
                KType def = entry.def;
                if(def != null)
                    entry.v = def;
                entry = (GOEntry) entry.next;
            }
        }
    }

    public void applyDefaults() {
        final Entry<?, ?>[] entries = this.entries;
        if (entries == null)
            return;
        for (int i = 0; i < length; i++) {
            GOEntry entry = (GOEntry) entries[i];
            if (entry == null)
                continue;
            while (entry != null) {
                entry.def = entry.v;
                entry = (GOEntry) entry.next;
            }
        }
    }

    public void markConst(String key) {
        KOEntry entry = (KOEntry) getEntry(key);

        if(entry != null)
            entry.flags |= 1;
    }

    public static class None extends GlobalVarMap {
        @Override
        protected MyHashMap.Entry<String, KType> createEntry(String id) {
            return new KOEntry(id, null);
        }

        @Override
        public void reset() {}

        @Override
        public void applyDefaults() {}
    }
}
