package roj.kscript.vm;

import roj.collect.SimpleList;
import roj.kscript.api.ArgList;
import roj.kscript.ast.Frame;
import roj.kscript.ast.InvokeNode;
import roj.kscript.parser.KParser;
import roj.kscript.parser.expr.ExprParser;
import roj.kscript.type.KInt;
import roj.kscript.type.KType;
import roj.math.MathUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * Kscript VM 资源管理器, 基本类型的alloc在这里进行
 *
 * @author Roj233
 * @since 2021/5/28 12:08
 */
public final class ResourceManager {
    static final ThreadLocal<ResourceManager> managerPerThread = ThreadLocal.withInitial(ResourceManager::new);

    public static ResourceManager get() {
        return managerPerThread.get();
    }

    static final int
            SHIFT_LENGTH = 8,
            LENGTH = 1 << SHIFT_LENGTH;

    static final int
            USAGE_MAX = MathUtils.parseInt(System.getProperty("kscript.usage_cache", "128")),
            CACHE_MAX = MathUtils.parseInt(System.getProperty("kscript.int_cache", "128")),
            PARSE_MAX = MathUtils.parseInt(System.getProperty("kscript.parser_cache", "32")),
            ARG_MAX = MathUtils.parseInt(System.getProperty("kscript.arg_cache", "64")),
            DEPTH_MAX = MathUtils.parseInt(System.getProperty("kscript.parser_depth", "50"));

    // region statistic

    static AtomicLong
            stat_c_all = new AtomicLong(),
            stat_c_hit = new AtomicLong(),
            stat_c_miss = new AtomicLong();

    public static void printStats() {
        System.out.println("Cache: Total " + stat_c_all.get() + " Hit " + stat_c_hit.get() + " Creation " + stat_c_miss.get());
    }

    public static void recordStats_stack_val_alloc() {
        stat_c_miss.incrementAndGet();
    }

    // endregion
    // region parse/invoke cache

    // number parse array
    private final int[] h_np = new int[1];

    // parser
    private final ExprParser[] h_ep = new ExprParser[PARSE_MAX];
    private final KParser[] h_kp = new KParser[PARSE_MAX];

    // invocation node
    private final ArrayList<VM_ArgList> h_arg = new ArrayList<>();
    private final ArrayList<SimpleList<KType>> h_arg_val = new ArrayList<>();

    public static int[] retainNumParseTmp(int radix) {
        int[] arr = get().h_np;
        arr[0] = radix;
        return arr;
    }

    public static ExprParser retainExprParser(int depth) {
        if(depth > DEPTH_MAX) {
            throw new IllegalArgumentException("Depth > " + DEPTH_MAX);
        }

        if (depth >= PARSE_MAX) {
            return new ExprParser(depth);
        }

        ExprParser[] arr = get().h_ep;
        if (arr[depth] == null) {
            arr[depth] = new ExprParser(depth);
        }

        return arr[depth];
    }

    public static KParser retainScriptParser(int depth, KParser parent) {
        if(depth > DEPTH_MAX) {
            throw new RuntimeException("Depth > " + DEPTH_MAX);
        }

        if (depth >= PARSE_MAX) {
            return new KParser(depth).reset(parent);
        }

        KParser[] arr = get().h_kp;
        if (arr[depth] == null) {
            arr[depth] = new KParser(depth);
        }

        return arr[depth].reset(parent);
    }

    public static List<KType> retainArgHolder(int argc) {
        ArrayList<SimpleList<KType>> arr = get().h_arg_val;
        SimpleList<KType> ls;
        if(arr.isEmpty()) {
            ls = new SimpleList<>();
        } else {
            ls = arr.remove(arr.size() - 1);
        }
        ls.ensureCapacity(argc);
        ls._int_setSize(argc);
        return ls;
    }

    public static ArgList retainArgList(InvokeNode node, Frame frame, List<KType> argsL) {
        ArrayList<VM_ArgList> arr = get().h_arg;
        return (arr.isEmpty() ? new VM_ArgList() : arr.remove(arr.size() - 1)).reset(node, frame, argsL);
    }

    public static void releaseArgObjs(ArgList args, List<KType> argsL) {
        ResourceManager man = get();
        ArrayList<SimpleList<KType>> arr1 = man.h_arg_val;
        if(arr1.size() < ARG_MAX) {
            argsL.clear();
            arr1.add((SimpleList<KType>) argsL);

            VM_ArgList args1 = (VM_ArgList) args;
            args1.caller = null;
            args1.from = null;
            args1.argv = null;

            man.h_arg.add(args1);
        }
    }

    // endregion
    // region primitive type

    //private VN_Float[][] h_f = new VN_Float[8][];
    //private VM_Long[][] h_l = new VM_Long[8][];
    private VM_Double[][] h_d = new VM_Double[8][];
    private VM_Int[][] h_i = new VM_Int[8][];

    private final ArrayList<Usage> usages = new ArrayList<>(), backup = new ArrayList<>();
    //ArrayList<SoftReference<VM_Float[]>> h_c_f = new ArrayList<>();
    //ArrayList<SoftReference<VM_Long[]>> h_c_l = new ArrayList<>();
    private final ArrayList<VM_Int[]> h_c_i = new ArrayList<>();
    private final ArrayList<VM_Double[]> h_c_d = new ArrayList<>();

    private int usage_f, usage_d, usage_i, usage_l;

    public void pushStack() {
        Usage u = backup.isEmpty() ? new Usage() : backup.get(backup.size() - 1);
        u.usage_f = usage_f;
        u.usage_d = usage_d;
        u.usage_i = usage_i;
        u.usage_l = usage_l;
        usages.add(u);
    }

    public void popStack() {
        Usage u = usages.remove(usages.size() - 1);
        if(backup.size() < USAGE_MAX)
            backup.add(u);
        removeCollected();

        int dt, min;

        /*dt = usage_f >>> SHIFT_LENGTH - u.usage_f >>> SHIFT_LENGTH;
        min = u.usage_f >>> SHIFT_LENGTH;
        while (dt > 0) {
            if(h_c_f.size() < CACHE_MAX) {
                h_c_f.add(new SoftReference<>(h_f[dt + min]));
            }
            h_f[dt-- + min] = null;
        }
        usage_f = u.usage_f;

        dt = usage_l >>> SHIFT_LENGTH - u.usage_l >>> SHIFT_LENGTH;
        min = u.usage_l >>> SHIFT_LENGTH;
        while (dt > 0) {
            if(h_c_l.size() < CACHE_MAX) {
                h_c_l.add(new SoftReference<>(h_l[dt + min]));
            }
            h_l[dt-- + min] = null;
        }
        usage_l = u.usage_l;*/

        min = u.usage_i >>> SHIFT_LENGTH;
        dt = (usage_i >>> SHIFT_LENGTH) - min - 1;
        while (dt > 0) {
            if(h_c_i.size() < CACHE_MAX) {
                h_c_i.add(h_i[dt + min]);
            }
            h_i[dt-- + min] = null;
        }
        usage_i = u.usage_i;

        min = u.usage_d >>> SHIFT_LENGTH;
        dt = (usage_d >>> SHIFT_LENGTH) - min - 1;
        while (dt > 0) {
            if(h_c_d.size() < CACHE_MAX) {
                h_c_d.add(h_d[dt + min]);
            }
            h_d[dt-- + min] = null;
        }
        usage_d = u.usage_d;
    }

    public void removeCollected() {
        /*for (int i = h_c_f.size() - 1; i >= 0; i--) {
            if(h_c_f.get(i).get() == null)
                h_c_f.remove(i);
        }
        for (int i = h_c_l.size() - 1; i >= 0; i--) {
            if(h_c_l.get(i).get() == null)
                h_c_l.remove(i);
        }
        for (int i = h_c_i.size() - 1; i >= 0; i--) {
            if(h_c_i.get(i).get() == null)
                h_c_i.remove(i);
        }
        for (int i = h_c_d.size() - 1; i >= 0; i--) {
            if(h_c_d.get(i).get() == null)
                h_c_d.remove(i);
        }*/
    }

    public KInt allocI(int v, int kind) {
        stat_c_all.getAndIncrement();

        if(kind == 3) {
            stat_c_miss.getAndIncrement();

            return KInt.valueOf(v);
        }

        int cur = usage_i++;
        int aid = cur >>> SHIFT_LENGTH;
        if(h_i.length <= aid) {
            VM_Int[][] NaNY = new VM_Int[h_i.length + 32][];
            System.arraycopy(h_i, 0, NaNY, 0, h_i.length);
            h_i = NaNY;
        }
        VM_Int[] h_partial = h_i[aid];
        if(h_partial == null)
            h_partial = h_i[aid] = gorI();

        VM_Int h = h_partial[cur & (LENGTH - 1)];
        if(h == null) {
            h_partial[cur & (LENGTH - 1)] = h = new VM_Int(this);
        } else {
            stat_c_hit.getAndIncrement();
        }

        h.value = v;
        return h;
    }

    public VM_Double allocD(double v, int kind) {
        int cur = usage_d++;
        int aid = cur >>> SHIFT_LENGTH;
        if(h_d.length <= aid) {
            VM_Double[][] NaNY = new VM_Double[h_d.length + 32][];
            System.arraycopy(h_d, 0, NaNY, 0, h_d.length);
            h_d = NaNY;
        }
        VM_Double[] h_partial = h_d[aid];
        if(h_partial == null)
            h_partial = h_d[aid] = gorD();

        VM_Double h = h_partial[cur & (LENGTH - 1)];
        if(h == null) {
            h_partial[cur & (LENGTH - 1)] = h = new VM_Double(this);
        }

        h.value = v;
        return h;
    }

    private VM_Int[] gorI() {
        if (h_c_i.isEmpty()) {
            System.out.println("Creation at " + usage_i);
            return new VM_Int[LENGTH];
        } else {
            return h_c_i.remove(h_c_i.size() - 1);
        }
    }

    private VM_Double[] gorD() {
        return h_c_d.isEmpty() ? new VM_Double[LENGTH] : h_c_d.remove(h_c_d.size() - 1);
    }

    static final class Usage {
        int usage_f, usage_d, usage_i, usage_l;
    }
}
