package roj.kscript.util;

import roj.collect.IntMap;
import roj.collect.LinkedIntMap;
import roj.collect.MyHashSet;
import roj.kscript.type.KType;

import java.util.Iterator;
import java.util.Set;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 * <p>
 * 作用域构造器
 *
 * @author Roj233
 * @since 2020/10/16 23:46
 */
public class RegionBuilder {
    private LinkedIntMap<Region> regions = new LinkedIntMap<>(2);
    private Region current;

    private int offset;
    private boolean same;

    public RegionBuilder() {
        regions.put(0, current = new Region());
        this.same = true;
    }

    public void regionEnter() {
        if (regions.lastValue().isEmpty())
            return;
        current = regions.lastValue().subRegion(offset);
        regions.put(/*current.getStart()*/offset, current);
    }

    public IntMap<BakedRegion> build() {
        IntMap<BakedRegion> map = new IntMap<>(regions.size(), 2);
        for (Iterator<IntMap.Entry<Region>> it = regions.entryIterator(); it.hasNext(); ) {
            IntMap.Entry<Region> entry = it.next();
            map.put(entry.getKey(), entry.getValue().bake());
        }

        //System.out.println("作用域: " + map);

        this.regions = null;
        this.current = null;
        this.same = false;

        return map;
    }

    public void offset(int offset) {
        if (offset != this.offset) {
            this.offset = offset;
            this.same = false;
        }
    }

    public void addVariable(String name, KType value) {
        if (!same) {
            regionEnter();
            same = true;
        }
        current.addVariable(name, value);
    }

    public void removeVariable(String name) {
        if (!same) {
            regionEnter();
            same = true;
        }
        current.removeVariable(name);
    }

    public boolean variableExists(String name) {
        return current.isVariableExists(name);
    }

    public Set<String> variables() {
        Set<String> set = new MyHashSet<>();
        current.variables(set);
        return set;
    }

    public KType getValue(String name) {
        return current.add.get(name);
    }
}
