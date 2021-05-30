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
public class ConstMap extends MyHashMap<String, KType> {
    public ConstMap() {
        super(8);
    }

    @Override
    protected MyHashMap.Entry<String, KType> createEntry(String id) {
        return new KOEntry(id, null);
    }

    public boolean isConst(String key) {
        KOEntry entry = (KOEntry) getEntry(key);
        return entry != null && (entry.flags & 1) != 0;
    }

    public void markConst(String key) {
        KOEntry entry = (KOEntry) getEntry(key);

        if(entry != null)
            entry.flags |= 1;
    }
}
