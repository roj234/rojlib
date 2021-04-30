package roj.config.data;

import roj.config.ObjectSerializer;

import javax.annotation.Nonnull;
import java.util.HashMap;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: CObject.java
 */
public final class CObject<T> extends CMapping {
    private T object;

    public CObject(T object) {
        super(Type.SERIALIZED_OBJECT, new HashMap<>());
        this.object = object;
    }

    @SuppressWarnings("unchecked")
    public CObject(CMapping map, ObjectSerializer<?> deserializer) {
        super(Type.SERIALIZED_OBJECT, map.map);
        this.object = ((ObjectSerializer<T>) deserializer).deserialize(this);
    }

    public void setObject(T object) {
        this.object = object;
    }

    public T getObject() {
        return this.object;
    }

    @Nonnull
    @Override
    @SuppressWarnings("unchecked")
    public <O> CObject<O> asObject(Class<O> clazz) {
        if (this.object == null)
            return (CObject<O>) this;
        else {
            if (clazz.isInstance(object)) {
                return (CObject<O>) this;
            }
            throw new ClassCastException(object.getClass() + " to " + clazz.getName());
        }
    }

    @Override
    public StringBuilder toJSON(StringBuilder sb, int depth) {
        if (this.object == null) return sb.append("null");
        map.clear();
        map.put("==", new CString(object.getClass().getName()));

        ObjectSerializer.find(this.object).serialize(this, this.object);

        return super.toJSON(sb, depth);
    }

    @Override
    public StringBuilder toYAML(StringBuilder sb, int depth) {
        if (this.object == null) return sb.append("null");
        map.clear();
        map.put("==", new CString(object.getClass().getName()));

        ObjectSerializer.find(this.object).serialize(this, this.object);

        return super.toYAML(sb, depth);
    }

}
