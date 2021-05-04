package roj.kscript.api;

import roj.kscript.KConstants;
import roj.kscript.func.KFunction;
import roj.kscript.type.KInt;
import roj.kscript.type.KType;
import roj.kscript.type.KUndefined;
import roj.kscript.type.Type;
import roj.kscript.util.JavaException;
import roj.math.MathUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/10/27 22:44
 */
public abstract class IArguments implements IObject {
    public List<KType> argv;

    @Nonnull
    public final KType get(int i) {
        return i < 0 || i >= argv.size() ? KUndefined.UNDEFINED : argv.get(i);
    }

    @Nullable
    public abstract KFunction caller();

    public final int getOr(int i, int def) {
        return i < 0 || i >= argv.size() ? def : argv.get(i).asInt();
    }

    public final double getOr(int i, double def) {
        return i < 0 || i >= argv.size() ? def : argv.get(i).asDouble();
    }

    public final String getOr(int i, String def) {
        return i < 0 || i >= argv.size() ? def : argv.get(i).asString();
    }

    public final boolean getOr(int i, boolean def) {
        return i < 0 || i >= argv.size() ? def : argv.get(i).asBool();
    }

    public final KType getOr(int i, KType def) {
        return i < 0 || i >= argv.size() ? def : argv.get(i);
    }

    public final <T> T getObject(int i, Class<T> t, T def) {
        return i < 0 || i >= argv.size() ? def : argv.get(i).asJavaObject(t).getOr(def);
    }

    public final <T> T getObject(int i, Class<T> t) {
        return getObject(i, t, null);
    }

    public List<StackTraceElement> trace() {
        ArrayList<StackTraceElement> collector = new ArrayList<>();
        trace(collector);
        return collector;
    }

    public abstract void trace(List<StackTraceElement> collector);

    public final int size() {
        return argv.size();
    }

    // just ignore it
    public void put(@Nonnull String key, KType entry) {}

    public boolean isInstanceOf(IObject obj) {
        return obj instanceof IArguments;
    }

    public final IObject getProto() {
        return KConstants.OBJECT;
    }

    public KType getOr(String key, KType def) {
        if("length".equals(key)) {
            return KInt.OnStack.valueOf(argv.size());
        }

        int[] arr = KConstants.getLocalIntParseArray(10);
        if(MathUtils.parseIntErrorable(key, arr)) {
            int i = arr[0];
            return i < 0 || i >= argv.size() ? KUndefined.UNDEFINED : argv.get(i);
        }
        throw new JavaException("无效的参数, 需要'length'或int32数字");
    }

    public final List<KType> getInternal() {
        return argv;
    }

    @Override
    public final boolean asBool() {
        return true;
    }

    public final Type getType() {
        return Type.OBJECT;
    }

    public StringBuilder toString0(StringBuilder sb, int depth) {
        return sb.append("[arguments: ").append(argv).append("]");
    }

    public boolean canCastTo(Type type) {
        return type == Type.OBJECT || type == Type.STRING;
    }
}
