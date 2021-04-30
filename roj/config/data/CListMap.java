package roj.config.data;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: CListMap.java
 */
public class CListMap extends ConfEntry {
    public String name;
    public ConfEntry value;

    public CListMap(String name, ConfEntry value) {
        super(Type.LIST_MAP);
        this.name = name;
        this.value = value;
    }


    @Override
    public StringBuilder toYAML(StringBuilder sb, int depth) {
        return sb.append(name).append(' ').append(value.toYAML(new StringBuilder(), depth + 2));
    }

    @Override
    public StringBuilder toJSON(StringBuilder sb, int depth) {
        throw new UnsupportedOperationException("JSON Supported this");
    }

    @Nonnull
    @Override
    public CListMap asListMap() {
        return this;
    }

    public ConfEntry getValue() {
        return value;
    }

    @Nonnull
    @Override
    public String asString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CListMap cListMap = (CListMap) o;

        if (!Objects.equals(name, cListMap.name)) return false;
        return Objects.equals(value, cListMap.value);
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }
}
