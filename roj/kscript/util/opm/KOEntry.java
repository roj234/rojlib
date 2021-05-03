package roj.kscript.util.opm;

import roj.collect.MyHashMap;
import roj.kscript.type.KType;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/4/25 23:13
 */
public class KOEntry extends MyHashMap.Entry<String, KType> {
    public byte flags;

    public KOEntry(String k, KType v) {
        super(k, v);
    }

    @Override
    public MyHashMap.Entry<String, KType> nextEntry() {
        KOEntry next = (KOEntry) this.next;
        if(next == null || (next.flags & 2) == 0)
            return next;
        return next.next;
    }
}
