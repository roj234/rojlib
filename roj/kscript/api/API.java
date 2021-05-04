package roj.kscript.api;

import roj.collect.TrieTree;
import roj.kscript.type.KDouble;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/10/14 22:46
 */
public final class API {
    public static final TrieTree<NativeMethod> NATIVE_METHODS = new TrieTree<>();

    public static boolean PRECOMPILE_NATIVE = true;
    
    static {
        // todo reflective
        NATIVE_METHODS.put("Math.pow", args -> KDouble.Intl.valueOf(
                Math.pow(args.get(0).asDouble(), args.get(1).asDouble())
        ));
        NATIVE_METHODS.put("Math.sqrt", args -> KDouble.Intl.valueOf(
                Math.sqrt(args.get(0).asDouble())
        ));
        NATIVE_METHODS.put("Math.sin", args -> KDouble.Intl.valueOf(
                Math.sin(args.get(0).asDouble())
        ));
        NATIVE_METHODS.put("Math.cos", args -> KDouble.Intl.valueOf(
                Math.cos(args.get(0).asDouble())
        ));
        NATIVE_METHODS.put("Math.abs", args -> KDouble.Intl.valueOf(
                Math.abs(args.get(0).asDouble())
        ));
    }


}
