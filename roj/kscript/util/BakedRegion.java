package roj.kscript.util;

import roj.asm.struct.attr.AttrLocalVars;
import roj.collect.Int2IntMap;
import roj.kscript.type.Context;
import roj.kscript.type.KType;
import roj.kscript.type.KUndefined;

import java.util.Arrays;
import java.util.Map;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/10/17 0:01
 */
public class BakedRegion {
    private final KV[] add;
    private final String[] remove;

    public BakedRegion(Region region) {
        if (!region.add.isEmpty()) {
            KV[] arr = new KV[region.add.size()];

            int i = 0;
            for (Map.Entry<String, KType> entry : region.add.entrySet()) {
                arr[i++] = new KV(entry.getKey(), entry.getValue() == null ? KUndefined.UNDEFINED : entry.getValue());
            }
            this.add = arr;
        } else {
            this.add = null;
        }
        if (region.remove.isEmpty()) {
            this.remove = null;
        } else {
            this.remove = region.remove.toArray(new String[region.remove.size()]);
        }
    }

    public void apply(Context context) {
        if (remove != null)
            for (String name : remove) {
                context.remove(name);
            }
        if (add != null)
            for (KV entry : add) {
                context.putInternal(entry.k, entry.v.copy());
            }
    }

    public void toVMCode(Int2IntMap toPosition, AttrLocalVars lvt) {
        // wip
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Region{");
        if (add != null) {
            sb.append("加:").append(Arrays.toString(add));
        }
        if (remove != null) {
            if (add != null)
                sb.append(", ");
            sb.append("减:").append(Arrays.toString(remove));
        }
        return sb.append('}').toString();
    }

    static class KV {
        final String k;
        final KType v;

        KV(String k, KType v) {
            this.k = k;
            this.v = v;
        }

        @Override
        public String toString() {
            return "'" + k + '\'' + '=' + v;
        }
    }
}
