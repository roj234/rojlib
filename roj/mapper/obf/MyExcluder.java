package roj.mapper.obf;

import roj.asm.tree.IClass;
import roj.asm.util.AccessFlag;
import roj.mapper.util.Desc;

/**
 * 排除常见的不能混淆的情况
 * @author Roj233
 * @since 2022/2/20 22:18
 */
public class MyExcluder {
    public static boolean isClassExclusive(IClass clz, Desc desc) {
        if (0 != (clz.accessFlag() & AccessFlag.ENUM)) {
            if (desc.name.equals("values")) return desc.param.startsWith("()");
            if (desc.name.equals("valueOf")) return desc.param.startsWith("(Ljava/lang/String;)");;
            if (desc.name.equals("VALUES")) return desc.param.startsWith("L");
        }
        return false;
    }
}
