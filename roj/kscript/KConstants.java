package roj.kscript;

import roj.collect.SimpleList;
import roj.kscript.api.IArguments;
import roj.kscript.api.IObject;
import roj.kscript.ast.Frame;
import roj.kscript.ast.InvokeNode;
import roj.kscript.func.KFuncNative;
import roj.kscript.func.KFunction;
import roj.kscript.parser.ExpressionParser;
import roj.kscript.parser.KParser;
import roj.kscript.type.*;
import roj.kscript.util.opm.ObjectPropMap;
import roj.math.MathUtils;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 * <p>
 * 系统常量定义
 *
 * @author Roj233
 * @since 2020/9/21 22:45
 */
public final class KConstants {
    static final int ARG_CACHE = MathUtils.parseInt(System.getProperty("kscript.arg_cache_max", "64"));
    static final int NUMBER_CACHE = MathUtils.parseInt(System.getProperty("kscript.number_cache_max", "1024"));
    static final int CACHE_MAX = MathUtils.parseInt(System.getProperty("kscript.parser.cache_max", "10"));
    static final int MAX_DEPTH = MathUtils.parseInt(System.getProperty("kscript.parser.depth_max", "50"));

    static final ThreadLocal<Object[]> KSCRIPT_SHARED_DATA = ThreadLocal.withInitial(() -> new Object[] {
            new int[1],
            new ExpressionParser[CACHE_MAX],
            new KParser[CACHE_MAX],
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>()
    });

    static AtomicInteger
            stat_number_hit = new AtomicInteger(),
            stat_arg_hit = new AtomicInteger(),
            stat_ep_hit = new AtomicInteger(),
            stat_kp_hit = new AtomicInteger(),

            stat_number_miss = new AtomicInteger(),
            stat_arg_miss = new AtomicInteger(),
            stat_ep_miss = new AtomicInteger(),
            stat_kp_miss = new AtomicInteger();

    public static void printStats() {
        int nh = stat_number_hit.get();
        int nm = stat_number_miss.get();
        System.out.println("NumberCache: Total " + (nh + nm) + " Hit " + nh + " Miss " + nm);
        nh = stat_arg_hit.get();
        nm = stat_arg_miss.get();
        System.out.println("ListArg: Total " + (nh + nm) + " Hit " + nh + " Miss " + nm);
        nh = stat_ep_hit.get();
        nm = stat_ep_miss.get();
        System.out.println("ExprParser: Total " + (nh + nm) + " Hit " + nh + " Miss " + nm);
        nh = stat_kp_hit.get();
        nm = stat_kp_miss.get();
        System.out.println("KParser: Total " + (nh + nm) + " Hit " + nh + " Miss " + nm);
    }

    public static int[] getLocalIntParseArray(int radix) {
        int[] arr = (int[]) KSCRIPT_SHARED_DATA.get()[0];
        arr[0] = radix;
        return arr;
    }

    public static ExpressionParser cachedEP(int depth) {
        ExpressionParser[] arr = (ExpressionParser[]) KSCRIPT_SHARED_DATA.get()[1];
        if (arr[0] == null) {
            for (int i = 0; i < CACHE_MAX; i++) {
                arr[i] = new ExpressionParser(i);
            }
        }

        if(depth > MAX_DEPTH) {
            throw new RuntimeException("Depth > " + MAX_DEPTH);
        }

        if (depth >= CACHE_MAX) {
            stat_ep_miss.getAndIncrement();
            return new ExpressionParser(depth);
        }

        stat_ep_hit.getAndIncrement();
        return arr[depth];
    }

    public static KParser cachedFP(int depth, KParser parent) {
        KParser[] arr = (KParser[]) KSCRIPT_SHARED_DATA.get()[2];
        if (arr[0] == null) {
            for (int i = 0; i < CACHE_MAX; i++) {
                arr[i] = new KParser(i);
            }
        }

        if(depth > MAX_DEPTH) {
            throw new RuntimeException("Depth > " + MAX_DEPTH);
        }

        if (depth >= CACHE_MAX) {
            stat_kp_miss.getAndIncrement();
            return new KParser(depth).reset(parent);
        }

        stat_kp_hit.getAndIncrement();
        return arr[depth].reset(parent);
    }

    @SuppressWarnings("unchecked")
    public static List<KType> retainArgsHolder(int argc) {
        ArrayList<SimpleList<KType>> arr = (ArrayList<SimpleList<KType>>) KSCRIPT_SHARED_DATA.get()[3];
        SimpleList<KType> ls;
        if(arr.isEmpty()) {
            stat_arg_miss.getAndIncrement();
            ls = new SimpleList<>();
        } else {
            stat_arg_hit.getAndIncrement();
            ls = arr.remove(arr.size() - 1);
        }
        ls.ensureCapacity(argc);
        ls.setSize(argc);
        return ls;
    }

    @SuppressWarnings("unchecked")
    public static InvArgs retainInvArgs(InvokeNode node, Frame frame, List<KType> argsL) {
        ArrayList<InvArgs> arr = (ArrayList<InvArgs>) KSCRIPT_SHARED_DATA.get()[4];
        if(arr.isEmpty())
            return new InvArgs(node, frame, argsL);
        else
            return arr.remove(arr.size() - 1).reset(node, frame, argsL);
    }

    @SuppressWarnings("unchecked")
    public static void releaseArgHolderAndInv(IArguments args, List<KType> argsL) {
        Object[] arr = KSCRIPT_SHARED_DATA.get();
        ArrayList<SimpleList<KType>> arr1 = (ArrayList<SimpleList<KType>>) arr[3];
        if(arr1.size() < ARG_CACHE) {
            ArrayList<InvArgs> arr2 = (ArrayList<InvArgs>) arr[4];

            argsL.clear();
            arr1.add((SimpleList<KType>) argsL);
            InvArgs args1 = (InvArgs) args;

            args1.caller = null;
            args1.callNode = null;
            args1.argv = null;

            arr2.add(args1);
        }
    }

    @SuppressWarnings("unchecked")
    public static KInt retainStackIntHolder(int i) {
        ArrayList<KInt.OnStack> arr = (ArrayList<KInt.OnStack>) KSCRIPT_SHARED_DATA.get()[5];
        if(arr.isEmpty()) {
            stat_number_miss.getAndIncrement();
            return new KInt.OnStack(i);
        } else {
            stat_number_hit.getAndIncrement();
            KInt.OnStack t = arr.remove(arr.size() - 1);
            t.s = 4;
            t.value = i;
            return t;
        }
    }

    @SuppressWarnings("unchecked")
    public static KDouble retainStackDoubleHolder(double d) {
        ArrayList<KDouble.OnStack> arr = (ArrayList<KDouble.OnStack>) KSCRIPT_SHARED_DATA.get()[6];
        if(arr.isEmpty()) {
            stat_number_miss.getAndIncrement();
            return new KDouble.OnStack(d);
        } else {
            stat_number_hit.getAndIncrement();
            KDouble.OnStack t = arr.remove(arr.size() - 1);
            t.s = 4;
            t.value = d;
            return t;
        }
    }

    @SuppressWarnings("unchecked")
    public static void releaseStackIntHolder(KInt.OnStack kInt) {
        ArrayList<KInt.OnStack> arr = (ArrayList<KInt.OnStack>) KSCRIPT_SHARED_DATA.get()[5];
        if(arr.size() < NUMBER_CACHE) {
            arr.add(kInt);
        }

    }

    @SuppressWarnings("unchecked")
    public static void releaseStackDoubleHolder(KDouble.OnStack kDouble) {
        ArrayList<KDouble.OnStack> arr = (ArrayList<KDouble.OnStack>) KSCRIPT_SHARED_DATA.get()[6];
        if(arr.size() < NUMBER_CACHE) {
            arr.add(kDouble);
        }
    }

    public static final KObject OBJECT = new KObject(null);

    public static final KObject FUNCTION = new KObject(OBJECT);

    public static final KFunction ARRAY = new KFuncNative() {
        @Override
        public KType invoke(@Nonnull IObject $this, IArguments param) {
            return new KArray(param.getOr(0, 0));
        }
    };

    public static final KFunction TYPEOF = new KFuncNative() {
        @Override
        public KType invoke(@Nonnull IObject $this, IArguments param) {
            return KString.valueOf(param.getOr(0, KUndefined.UNDEFINED).getType().typeof());
        }
    };

    static {
        //OBJECT.put("toString", new KMethodAST(AST.builder().op(LOAD_OBJECT, 0).op(INVOKE_SPECIAL_METHOD, "toString").returnStack().build()));
        OBJECT.put("defineProperty", new KFuncNative() {
            @Override
            public KType invoke(@Nonnull IObject $this, IArguments param) {
                if(param.size() < 2)
                    throw new IllegalArgumentException("Need 2, get " + param.size());
                ObjectPropMap.Object_defineProperty($this.asKObject(), param.get(0).asString(), param.get(1).asObject());
                return KUndefined.UNDEFINED;
            }
        });
    }
}
