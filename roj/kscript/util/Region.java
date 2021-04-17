package roj.kscript.util;

import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.kscript.type.KType;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/10/16 23:36
 */
public final class Region {
    private Region parent;

    private final int start;
    private int length;

    Map<String, KType> add = Collections.emptyMap();
    Set<String> remove = Collections.emptySet();

    public Region() {
        this.start = 0;
    }

    private Region(Region parent, int start) {
        this.parent = parent;
        this.start = start;
    }

    public BakedRegion bake() {
        BakedRegion region = new BakedRegion(this);
        add = null;
        remove = null;
        return region;
    }

    Region subRegion(int offset) {
        return new Region(this, start + (this.length = offset));
    }

    int getStart() {
        return start;
    }

    int getLength() {
        return length;
    }

    void addVariable(String name, KType def) {
        if (remove.contains(name))
            throw new IllegalStateException("变量" + name + "既添加又删除");
        if (add == Collections.EMPTY_MAP) {
            add = new MyHashMap<>(2);
        }
        add.put(name, def);
    }

    void removeVariable(String name) {
        if (add.containsKey(name)) { // will replace
            System.err.println("Current scope already contains " + name);
            return;
        }
        if (remove == Collections.EMPTY_SET) {
            remove = new MyHashSet<>(2);
        }
        remove.add(name);
    }

    boolean isVariableExists(String name) {
        if (add.containsKey(name))
            return true;
        if (remove.contains(name))
            return false;
        return parent != null && parent.isVariableExists(name);
    }

    void variables(Set<String> set) {
        if (parent != null)
            parent.variables(set);
        set.removeAll(remove);
        set.addAll(add.keySet());
    }

    public boolean isEmpty() {
        return add.isEmpty() && remove.isEmpty();
    }
}
