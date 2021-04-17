package roj.asm.mapper.util;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/7/21 2:42
 */
public class NameAndType {
    public String owner, name, type;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NameAndType that = (NameAndType) o;
        return name.equals(that.name) && type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return name.hashCode() * 31 + type.hashCode();
    }

    @Override
    public String toString() {
        return name + ' ' + type;
    }

    public NameAndType copy() {
        NameAndType nat = new NameAndType();
        nat.name = name;
        nat.type = type;
        return nat;
    }
}
