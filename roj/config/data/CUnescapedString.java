package roj.config.data;

import roj.config.serial.Structs;
import roj.util.ByteList;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * 适用于特定配置格式的已序列化对象
 * @author Roj234
 * @since 2022/3/11 20:18
 */
public final class CUnescapedString extends CEntry {
    private final CharSequence v;

    public CUnescapedString(CharSequence string) {
        this.v = string;
    }

    @Nonnull
    @Override
    public Type getType() {
        return Type.UNESCAPED_STRING;
    }

    public static CUnescapedString valueOf(CharSequence s) {
        return new CUnescapedString(s);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CUnescapedString that = (CUnescapedString) o;
        return Objects.equals(v, that.v);
    }

    @Override
    public int hashCode() {
        return v == null ? 0 : v.hashCode();
    }

    @Override
    public StringBuilder toJSON(StringBuilder sb, int depth) {
        return sb.append(v);
    }

    @Override
    public Object unwrap() {
        throw new IllegalStateException();
    }

    @Override
    public void toBinary(ByteList w, Structs struct) {
        throw new IllegalStateException();
    }
}
