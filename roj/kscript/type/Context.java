package roj.kscript.type;

import roj.annotation.Internal;
import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.kscript.api.IGettable;
import roj.kscript.util.BakedRegion;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 * <p>
 * 函数变量上下文
 *
 * @author Roj233
 * @since 2020/9/28 0:55
 */
public class Context implements IGettable {
    Context parent;
    Map<String, KType> map;

    Context(Context parent, IntMap<BakedRegion> regions) {
        this.parent = parent;
        this.regions = regions;
        this.map = new MyHashMap<>();
    }

    @Override
    public final boolean canCastTo(Type type) {
        return type == Type.OBJECT;
    }

    Context(Context parent) {
        this.parent = parent;
        this.map = new MyHashMap<>();
    }

    @Override
    public final void put(@Nonnull String key, KType entry) {
        createPrototyped(key, entry, false);
    }

    @Nonnull
    @Override
    public final KType get(String key) {
        KType base = getPrototyped(key, null);
        if (base == null)
            throw new IllegalArgumentException("Undefined variable " + key);
        return base;
    }

    protected final KType getPrototyped(String keys, KType defaultValue) {
        if (keys == null) {
            return defaultValue;
        }
        KType base = map.get(keys);
        if (base != null) {
            return base;
        } else {
            return parent == null ? defaultValue : parent.getPrototyped(keys, defaultValue);
        }
    }

    @Override
    public final boolean isInstanceOf(IGettable map) {
        return map == this;
    }

    @Override
    public final IGettable getPrototype() {
        return parent;
    }

    @Override
    public final KType getOr(String id, KType kb) {
        return getPrototyped(id, kb);
    }

    @Override
    public final int size() {
        return map.size();
    }

    final boolean createPrototyped(String keys, KType value, boolean f) {
        if (map.containsKey(keys)) {
            map.put(keys, value);
            return true;
        }
        if (parent == null)
            throw new IllegalArgumentException("Undefined variable " + keys);
        return parent.createPrototyped(keys, value, true);
    }

    // region 作用域

    IntMap<BakedRegion> regions;
    private int regionId = -1;

    public void enterRegion(int id) {
        if (id > regionId) {
            BakedRegion region = regions.get(id);
            if (region != null)
                region.apply(this);
            regionId = id;
        }
    }

    @Internal
    public final void putInternal(String k, KType v) {
        map.put(k, v);
    }

    public Context init() {
        regionId = -1;
        return this;
    }

    // endregion

    @Override
    public final Type getType() {
        return Type.OBJECT;
    }

    @Override
    public final StringBuilder toString0(StringBuilder sb, int depth) {
        return sb.append("[CONTEXT]");
    }

    @Override
    public final Context copy() {
        return new Context(parent, regions);
    }

    @Internal
    public final void remove(String name) {
        map.remove(name);
    }

    @Internal
    public final void reset() {
        map.clear();
        regionId = -1;
    }
}
