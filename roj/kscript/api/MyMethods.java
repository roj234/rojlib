package roj.kscript.api;

import roj.collect.TrieTree;
import roj.kscript.type.KDouble;
import roj.kscript.type.KType;

import java.util.List;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/5/27 1:11
 */
public class MyMethods extends MethodsAPI {
    private final TrieTree<PreComputeMethod> preComputeList = new TrieTree<>();

    public MyMethods() {
        // todo reflective
        preComputeList.put("Math.pow", args -> KDouble.valueOf(
                Math.pow(args.get(0).asDouble(), args.get(1).asDouble())
        ));
        preComputeList.put("Math.sqrt", args -> KDouble.valueOf(
                Math.sqrt(args.get(0).asDouble())
        ));
        preComputeList.put("Math.sin", args -> KDouble.valueOf(
                Math.sin(args.get(0).asDouble())
        ));
        preComputeList.put("Math.cos", args -> KDouble.valueOf(
                Math.cos(args.get(0).asDouble())
        ));
        preComputeList.put("Math.abs", args -> KDouble.valueOf(
                Math.abs(args.get(0).asDouble())
        ));
    }

    @Override
    protected Computer getDedicated0(CharSequence name) {
        return null;
    }

    @Override
    protected KType preCompute0(CharSequence name, List<KType> cst) {
        return preComputeList.getOrDefault(name, PreComputeMethod.NULL).handle(cst);
    }
}
