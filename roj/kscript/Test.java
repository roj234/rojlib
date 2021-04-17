package roj.kscript;

import roj.config.ParseException;
import roj.io.IOUtil;
import roj.kscript.func.KFuncLambda;
import roj.kscript.func.KFunction;
import roj.kscript.func.KInitializer;
import roj.kscript.parser.KParser;
import roj.kscript.type.*;
import roj.kscript.util.ObjectBuilder;
import roj.text.TextUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: null.java
 */
public class Test {
    static long last;

    public static void main(String[] args) throws IOException, ParseException {
        String data = IOUtil.readAsUTF(new FileInputStream(new File(args[0])));

        ContextPrimer root = new ContextPrimer(null);
        root.define("println", new KFuncLambda(($this, param) -> {
            final KType base = param.getOr(0, KUndefined.UNDEFINED);
            System.out.println("P: " + (base.canCastTo(Type.STRING) ? base.asString() : base.toString()));
            return KUndefined.UNDEFINED;
        }));
        root.define("timer", new KFuncLambda(($this, param) -> {
            System.out.println("距离上次调用: " + TextUtil.getScaledNumber(System.currentTimeMillis() - last) + "MS");
            last = System.currentTimeMillis();
            return KUndefined.UNDEFINED;
        }));
        root.define("stackTrace", new KFuncLambda(($this, param) -> {
            Throwable t = new Throwable();
            final StackTraceElement[] trace = param.trace();
            trace[0] = new StackTraceElement("roj.kscript.Test", "stackTrace", "Test.java", 44);

            t.setStackTrace(trace);
            t.printStackTrace();
            return KUndefined.UNDEFINED;
        }));
        root.define("Array", new KInitializer((param) -> {
            return new KArray(param.getOr(0, 0));
        }));
        root.define("Date", new KInitializer((param) -> {
            return KInteger.valueOf((int) (System.currentTimeMillis() & Long.MAX_VALUE));
        }));

        KObject math = ObjectBuilder.builder()
                .returns("pow", argList -> {
                    return KDouble.valueOf(Math.pow(argList.getOr(0, 0d), argList.getOr(1, 0d)));
                }).returns("sin", argList -> {
                    return KDouble.valueOf(Math.sin(argList.getOr(0, 0d)));
                }).returns("cos", argList -> {
                    return KDouble.valueOf(Math.cos(argList.getOr(0, 0d)));
                }).build();
        root.define("Math", math);

        KParser parser = new KParser(root);

        KFunction function = parser.parse(args[0], data);

        function.invoke(KNull.NULL, new Arguments());
    }
}
