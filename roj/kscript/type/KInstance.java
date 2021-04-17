package roj.kscript.type;

import roj.collect.MyHashMap;
import roj.kscript.Arguments;
import roj.kscript.api.IGettable;
import roj.kscript.func.KFunction;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/9/27 23:41
 */
public final class KInstance extends KObject {
    final String cst;

    @Override
    public boolean canCastTo(Type type) {
        switch (type) {
            case INSTANCE:
            case OBJECT:
                return true;
        }
        return false;
    }

    public KInstance(KFunction constructor, IGettable iMethod) {
        super(Type.INSTANCE, new MyHashMap<>(iMethod.size()), new KObject(iMethod));
        for (Map.Entry<String, KType> entry : iMethod.getInternalMap().entrySet()) {
            String key = entry.getKey();
            KType value = entry.getValue();

            if (value.getType() != Type.FUNCTION)
                map.put(key, value);
            else {
                map.put(key, new InstanceFunc(this, value.asFunction()));
            }
        }

        this.cst = constructor.getName();
    }

    private static class InstanceFunc extends KFunction {
        final KObject $this;
        final KFunction original;

        public InstanceFunc(KObject $this, KFunction original) {
            super(original);
            this.$this = $this;
            this.original = original;
        }

        @Override
        public KType invoke(@Nonnull IGettable $this, Arguments param) {
            return original.invoke(this.$this, param);
        }

        @Override
        public String getClassName() {
            return original.getClassName();
        }
    }

    @Override
    public KType copy() {
        return this;
    }
}
