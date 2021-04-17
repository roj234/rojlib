package roj.kscript.type;

import roj.annotation.Internal;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.config.word.AbstLexer;
import roj.kscript.api.IGettable;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: KObject.java
 */
public class KObject extends KBase implements IGettable {
    protected final Map<String, KType> map;
    IGettable parent;

    public KObject(IGettable parent) {
        this(parent, 2);
    }

    public KObject(IGettable parent, int capacity) {
        this(Type.OBJECT, new MyHashMap<>(capacity), parent);
    }

    @Internal
    public KObject(Type type, Map<String, KType> map, IGettable parent) {
        super(type);
        this.map = map;
        this.parent = parent;
        if (parent != null)
            map.put("prototype", parent);
    }

    public int size() {
        return map.size();
    }

    @Nonnull
    public Set<Map.Entry<String, KType>> entrySet() {
        return map.entrySet();
    }

    @Override
    public void put(@Nonnull String key, KType entry) {
        createPrototyped(key, entry == null ? KUndefined.UNDEFINED : entry, false);
    }

    protected KType getPrototyped(String keys, KType defaultValue) {
        if (keys == null) {
            return defaultValue;
        }
        KType base = map.get(keys);
        if (base != null) {
            return base;
        } else {
            return !(parent instanceof KObject) ? defaultValue : ((KObject) parent).getPrototyped(keys, defaultValue);
        }
    }

    boolean createPrototyped(String keys, KType value, boolean f) {
        if (map.containsKey(keys)) {
            map.put(keys, value);
            return true;
        } else if (parent == null) {
            if (!f) {
                map.put(keys, value);
                return true;
            }
            return false;
        }

        boolean superGot = parent instanceof KObject && ((KObject) parent).createPrototyped(keys, value, true);
        if (!superGot) {
            map.put(keys, value);
        }
        return true;
    }

    @Nonnull
    @Override
    public KObject asKObject() {
        return this;
    }

    public void merge(KObject anotherMapping, boolean selfBetter, boolean deep) {
        if (!deep) {
            if (!selfBetter) {
                this.map.putAll(anotherMapping.map);
            } else {
                Map<String, KType> map1 = new HashMap<>(this.map);
                this.map.clear();
                this.map.putAll(anotherMapping.map);
                this.map.putAll(map1);
            }
        } else {
            Map<String, KType> map = new HashMap<>(anotherMapping.map);
            for (Map.Entry<String, KType> entryEntry : this.map.entrySet()) {
                KType entry = entryEntry.getValue();
                KType entry1 = map.remove(entryEntry.getKey());
                if (entry1 != null) {
                    if (entry.canCastTo(entry1.getType())) {
                        switch (entry.getType()) {
                            case OBJECT:
                                if (!selfBetter)
                                    entry1.asKObject().merge(entry.asKObject(), false, true);
                                else
                                    entry.asKObject().merge(entry1.asKObject(), true, true);
                                break;
                            case ARRAY:
                                if (!selfBetter)
                                    entry1.asArray().addAll(entry.asArray());
                                else
                                    entry.asArray().addAll(entry1.asArray());
                                break;
                        }
                    }
                    if (!selfBetter)
                        entryEntry.setValue(entry1);
                }
            }
            this.map.putAll(map);
        }
    }

    @Override
    public boolean equalsTo(KType b) {
        // todo 提高效率
        Map<String, KType> types = map;
        Map<String, KType> types1 = b.asObject().getInternalMap();

        if (types.size() != types1.size())
            return false;

        if (types.isEmpty())
            return true;

        Iterator<Map.Entry<String, KType>> itra;
        Iterator<Map.Entry<String, KType>> itrb;
        List<Map.Entry<String, KType>> tmp = new ArrayList<>(types.entrySet());
        tmp.sort((o1, o2) -> Integer.compare(o1.getKey().hashCode(), o2.getKey().hashCode()));
        itra = tmp.iterator();
        tmp = new ArrayList<>(types1.entrySet());
        tmp.sort((o1, o2) -> Integer.compare(o1.getKey().hashCode(), o2.getKey().hashCode()));
        itrb = tmp.iterator();


        while (itra.hasNext()) {
            final Map.Entry<String, KType> next = itra.next();
            final Map.Entry<String, KType> next1 = itrb.next();
            if (next == null) {
                if (next1 != null)
                    return false;
            } else if (!next.getKey().equals(next1.getKey()) || !next1.getValue().equalsTo(next.getValue()))
                return false;
        }
        return true;
    }

    @Override
    public StringBuilder toString0(StringBuilder sb, int depth) {
        sb.append('{');
        if (!map.isEmpty()) {
            sb.append('\n');
            for (Map.Entry<String, KType> entry : map.entrySet()) {
                for (int i = 0; i < depth; i++) {
                    sb.append(' ');
                }

                sb.append('"').append(AbstLexer.addSlashes(entry.getKey())).append('"').append(':').append(' ');
                entry.getValue().toString0(sb, depth + 4).append(',').append('\n');
            }
            sb.delete(sb.length() - 2, sb.length() - 1);
            for (int i = 0; i < depth - 4; i++) {
                sb.append(' ');
            }
        }
        return sb.append('}');
    }

    @Override
    public KType copy() {
        return new KObject(Type.OBJECT, new MyHashMap<>(this.map), parent);
    }

    public IGettable deepCopy() {
        KObject obj = new KObject(Type.OBJECT, new MyHashMap<>(this.map.size()), parent);
        obj.merge(this, false, true);
        return obj;
    }

    @Override
    public boolean canCastTo(Type type) {
        return type == Type.OBJECT;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        KObject mapping = (KObject) o;

        return equalsTo(mapping);
    }

    @Override
    public int hashCode() {
        return map != null ? map.hashCode() : 0;
    }

    @Override
    public boolean asBoolean() {
        return true;
    }

    @Override
    public boolean isInstanceOf(IGettable map) {
        if (!(map instanceof KObject)) return false;

        Set<IGettable> prototypes = new MyHashSet<>();
        KObject parent = this;
        while (true) {
            prototypes.add(parent);
            if (!(parent.parent instanceof KObject))
                break;
            parent = (KObject) parent.parent;
        }

        parent = (KObject) map;
        while (true) {
            if (prototypes.contains(parent))
                return true;
            if (!(parent.parent instanceof KObject))
                break;
            parent = (KObject) parent.parent;
        }
        return false;
    }

    public Map<String, KType> getInternalMap() {
        return map;
    }

    @Override
    public IGettable getPrototype() {
        return parent;
    }

    @Override
    public KType getOr(String id, KType kb) {
        return getPrototyped(id, kb);
    }
}
