package roj.asm.util;

import roj.asm.struct.attr.Attribute;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;

import java.util.Map;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/10/25 16:50
 */
public final class AttributeList extends SimpleList<Attribute> {
    private Map<String, Attribute> byName;

    public void putByName(String name, Attribute attribute) {
        int index = -1;
        for (int i = 0; i < size; i++) {
            if (((Attribute) list[i]).name.equals(name)) {
                index = i;
                break;
            }
        }
        if (index == -1)
            add(attribute);
        else
            list[index] = attribute;
        initMap().put(name, attribute);
    }

    public Object getByName(String name) {
        return initMap().get(name);
    }

    public boolean removeByName(String name) {
        Object o = initMap().get(name);
        if (o == null)
            return false;
        return remove(o);
    }

    @Override
    public boolean add(Attribute attribute) {
        Object attr = initMap().get(attribute.name);
        if (attr != null) {
            super.set(indexOf(attr), attribute);
            return true;
        } else {
            return super.add(attribute);
        }
    }

    private Map<String, Attribute> initMap() {
        if (byName == null) {
            byName = new MyHashMap<>(size());
            for (int i = 0; i < size; i++) {
                Attribute attr = (Attribute) list[i];
                byName.put(attr.name, attr);
            }
        }
        return byName;
    }

    @Override
    protected void handleAdd(int pos, Attribute element) {
        if (byName != null)
            byName.put(element.name, element);
    }

    @Override
    protected void handleAdd(int pos, Attribute[] elements, int i, int length) {
        if (byName != null) {
            for (; i < length; i++) {
                Attribute element = elements[i];
                byName.put(element.name, element);
            }
        }
    }

    @Override
    protected void handleRemove(int pos, Attribute element) {
        if (byName != null)
            byName.remove(element.name);
    }

    @Override
    protected void handleRemove(Object[] elements, int length) {
        if (byName != null) {
            for (int i = 0; i < length; i++) {
                byName.remove(elements[i]);
            }
        }
    }

    public AttributeList() {
    }

    public AttributeList(int capacity) {
        super(capacity);
    }
}
