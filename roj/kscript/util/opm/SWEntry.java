package roj.kscript.util.opm;

import roj.collect.MyHashMap;
import roj.kscript.ast.Node;
import roj.kscript.ast.VInfo;
import roj.kscript.type.KType;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/4/25 23:13
 */
public class SWEntry extends MyHashMap.Entry<KType, Node> {
    public VInfo diff;

    public SWEntry(KType k, Node v) {
        super(k, v);
    }
}
