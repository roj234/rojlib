package roj.kscript.util.opm;

import roj.collect.MyHashMap;
import roj.kscript.type.KType;

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
                entry.reset();
                entry = (GOEntry) entry.next;
            }
        }
    }

    public GlobalVarMap copy() {
        final Entry<?, ?>[] entries = this.entries;
        GlobalVarMap other = new GlobalVarMap();
        other.ensureCapacity(size);
        if (entries == null)
            return other;

        for (int i = 0; i < length; i++) {
            GOEntry entry = (GOEntry) entries[i];
            if (entry == null)
                continue;
            while (entry != null) {
                GOEntry copy = (GOEntry) other.getOrCreateEntry(entry.k);
                copy.flags = entry.flags;
                copy.def = entry.def;
                copy.chk = entry.chk;
                copy.v = entry.v;
                entry = (GOEntry) entry.next;
            }
        }

        return other;
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
                entry.init();
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

        public GlobalVarMap copy() {
            final Entry<?, ?>[] entries = this.entries;
            GlobalVarMap other = new GlobalVarMap();
            other.ensureCapacity(size);
            if (entries == null)
                return other;

            for (int i = 0; i < length; i++) {
                KOEntry entry = (KOEntry) entries[i];
                if (entry == null)
                    continue;
                while (entry != null) {
                    KOEntry copy = (KOEntry) other.getOrCreateEntry(entry.k);
                    copy.flags = entry.flags;
                    copy.v = entry.v;
                    entry = (GOEntry) entry.next;
                }
            }

            return other;
        }
    }
}
