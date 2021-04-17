package roj.config;

import roj.config.data.CObject;

import java.util.HashMap;
import java.util.Map;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: ObjectSerializer.java
 */
public interface ObjectSerializer<O> {
    Map<String, ObjectSerializer<?>> REGISTRY = new HashMap<>();

    @SuppressWarnings("unchecked")
    static <T> ObjectSerializer<T> find(T obj) {
        ObjectSerializer<T> serializer = (ObjectSerializer<T>) REGISTRY.get(obj.getClass().getName());

        if (serializer == null)
            throw new NullPointerException("ObjectSerializer for " + obj.getClass().getName());
        return serializer;
    }

    static <T> void register(Class<T> clazz, ObjectSerializer<T> serializer) {
        REGISTRY.put(clazz.getName(), serializer);
    }

    O deserialize(CObject<O> object);

    void serialize(CObject<O> base, O object);
}
