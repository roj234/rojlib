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
public class GOEntry extends KOEntry {
    public KType def;

    public GOEntry(String k, KType v) {
        super(k, v);
    }

    @Override
    public MyHashMap.Entry<String, KType> nextEntry() {
        return this.next;
    }
}
