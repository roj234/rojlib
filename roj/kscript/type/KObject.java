package roj.kscript.type;

import roj.annotation.Internal;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.config.word.AbstLexer;
import roj.kscript.api.IObject;
import roj.kscript.util.opm.KOEntry;
import roj.kscript.util.opm.ObjectPropMap;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: KObject.java
 */
public class KObject extends KBase implements IObject {
    protected final MyHashMap<String, KType> map;
    protected IObject parent;

    public KObject(IObject proto) {
        super(Type.OBJECT);
        this.map = new ObjectPropMap();
        if(proto != null)
            map.put("_proto_", proto);
    }

    @Internal
    public KObject(Type type, MyHashMap<String, KType> map, IObject proto) {
        super(type);
        this.map = map;
        if(proto != null)
            map.put("_proto_", proto);
    }

    public boolean delete(String id) {
        return map.remove(id) != null;
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
        if("_proto_".equals(key)) {
            parent = entry.canCastTo(Type.OBJECT) ? entry.asObject() : null;
            return;
        }
        putItr(key, entry == null ? KUndefined.UNDEFINED : entry.markImmutable(true), false);
    }

    boolean putItr(String id, KType value, boolean f) {
        KOEntry entry = (KOEntry) map.getEntry(id);
        if (entry != null) {
            if((entry.flags & 1) != 0)
                throw new IllegalStateException("Write to constant");
            entry.setValue(value);
            return true;
        } else {
            if(parent != null) {
                if(parent instanceof KObject) {
                    if(!((KObject) parent).putItr(id, value, false)) {
                        map.put(id, value);
                        // fallback if not found
                    }
                } else {
                    if(parent.getOrNull(id) != null) {
                        parent.put(id, value);
                    }
                }
            }
        }
        return false;
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

    //@Override
    public boolean __int_equals__(KType map) {
        Map<String, KType> as = this.map;
        Map<String, KType> bs = map.asKObject().getInternal();

        if (as.size() != bs.size())
            return false;

        if (as.isEmpty())
            return true;

        List<Map.Entry<String, KType>> tmp = new ArrayList<>(as.entrySet());
        tmp.sort((o1, o2) -> Integer.compare(o1.getKey().hashCode(), o2.getKey().hashCode()));
        Iterator<Map.Entry<String, KType>> itra = tmp.iterator();

        tmp.clear();
        tmp.addAll(bs.entrySet());
        tmp.sort((o1, o2) -> Integer.compare(o1.getKey().hashCode(), o2.getKey().hashCode()));
        Iterator<Map.Entry<String, KType>> itrb = tmp.iterator();

        while (itra.hasNext()) {
            Map.Entry<String, KType> ea = itra.next();
            Map.Entry<String, KType> eb = itrb.next();
            if(!ea.getKey().equals(eb.getKey()))
                return false;

            KType a = ea.getValue();
            KType b = eb.getValue();
            if(a != b) {
                if (a == null || a.getType() != b.getType() || !a.equalsTo(b)) return false;
            }
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
        return new KObject(Type.OBJECT, new ObjectPropMap(this.map), parent);
    }

    @Override
    public void copyFrom(KType type) {
        // Not clear? maybe another custom map
        this.map.clear();
        this.map.putAll(type.asKObject().map);
    }

    public IObject deepCopy() {
        KObject obj = new KObject(Type.OBJECT, new ObjectPropMap(this.map.size()), parent);
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

        return ((KObject) o).map == map;
    }

    @Override
    public int hashCode() {
        return map != null ? map.hashCode() : 0;
    }

    @Override
    public boolean asBool() {
        return true;
    }

    @Override
    public boolean isInstanceOf(IObject obj) {
        if (!(obj instanceof KObject)) return false;

        Set<IObject> prototypes = new MyHashSet<>();
        KObject parent = this;
        while (true) {
            prototypes.add(parent);
            if (!(parent.parent instanceof KObject))
                break;
            parent = (KObject) parent.parent;
        }

        parent = (KObject) obj;
        while (true) {
            if (prototypes.contains(parent))
                return true;
            if (!(parent.parent instanceof KObject))
                break;
            parent = (KObject) parent.parent;
        }
        return false;
    }

    public MyHashMap<String, KType> getInternal() {
        return map;
    }

    @Override
    public IObject getProto() {
        return parent;
    }

    @Override
    public KType getOr(String key, KType def) {
        KOEntry base = (KOEntry) map.getEntry(key);
        if (base != null) {
            // once load
            return base.getValue().markImmutable(false);
        } else {
            return parent == null ? def : parent.getOr(key, def);
        }
    }
}
