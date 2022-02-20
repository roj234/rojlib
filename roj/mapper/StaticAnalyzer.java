package roj.mapper;

import roj.asm.cst.CstClass;
import roj.asm.util.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 静态分析方法调用, 因为无法处理反射, 建议与{@link roj.misc.CpFilter 动态分析}取并集
 * @author Roj233
 * @since 2022/2/20 1:34
 */
public class StaticAnalyzer {
    public static void analyzeFrom(List<String> entryPoint, Map<String, Context> data, int flag) {
        List<String> pending = new ArrayList<>(entryPoint),
                next = new ArrayList<>(),
                tmp;
        do {
            for (int i = 0; i < pending.size(); i++) {
                Context ctx = data.get(pending.get(i));
                if (ctx != null)
                collectCall(ctx, next, flag);
            }

            tmp = pending;
            pending = next;
            next = tmp;
            next.clear();
        } while (!pending.isEmpty());
    }

    private static void collectCall(Context ctx, List<String> next, int flag) {
        if (flag == 0) {
            List<CstClass> classes = ctx.getClassConstants();
            for (int i = 0; i < classes.size(); i++) {
                next.add(classes.get(i).getValue().getString());
            }
        } else {
            ctx.getData().getMethodByName("main");
        }
    }
}
