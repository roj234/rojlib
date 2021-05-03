package roj.kscript.api;

import roj.collect.TrieTree;
import roj.kscript.parser.ExpressionParser;
import roj.kscript.parser.KParser;
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

    static {
        // todo reflective
        NATIVE_METHODS.put("Math.pow", args -> KDouble.valueOf(
                Math.pow(args.get(0).asDouble(), args.get(1).asDouble())
        ));
        NATIVE_METHODS.put("Math.sqrt", args -> KDouble.valueOf(
                Math.sqrt(args.get(0).asDouble())
        ));
        NATIVE_METHODS.put("Math.sin", args -> KDouble.valueOf(
                Math.sin(args.get(0).asDouble())
        ));
        NATIVE_METHODS.put("Math.cos", args -> KDouble.valueOf(
                Math.cos(args.get(0).asDouble())
        ));
        NATIVE_METHODS.put("Math.abs", args -> KDouble.valueOf(
                Math.abs(args.get(0).asDouble())
        ));
    }

    public static boolean PRECOMPILE_NATIVE = true;

    public static int PARSER_CACHE_MAX = 10;
    private static ExpressionParser[] parsers;
    private static KParser[] fileParsers;

    public static ExpressionParser cachedEP(int depth) {
        if (parsers == null) {
            parsers = PARSER_CACHE_MAX == 0 ? null : new ExpressionParser[PARSER_CACHE_MAX];
            for (int i = 0; i < PARSER_CACHE_MAX; i++) {
                parsers[i] = new ExpressionParser(i);
            }
        }
        if(depth > 50) {
            throw new RuntimeException("Depth > 50");
        }
        return depth >= PARSER_CACHE_MAX ? new ExpressionParser(depth) : parsers[depth];
    }

    public static KParser cachedFP(int depth, KParser parent) {
        if (fileParsers == null) {
            fileParsers = PARSER_CACHE_MAX == 0 ? null : new KParser[PARSER_CACHE_MAX];
            for (int i = 0; i < PARSER_CACHE_MAX; i++) {
                fileParsers[i] = new KParser(i);
            }
        }
        if(depth > 50) {
            throw new RuntimeException("Depth > 50");
        }
        return (depth >= PARSER_CACHE_MAX ? new KParser(depth) : fileParsers[depth]).reset(parent);
    }
}
