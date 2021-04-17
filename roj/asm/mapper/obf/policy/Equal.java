package roj.asm.mapper.obf.policy;

import roj.asm.mapper.util.Desc;

import java.util.Random;
import java.util.Set;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/7/18 23:23
 */
public class Equal implements NamingFunction {
    public static final Equal INSTANCE = new Equal();

    @Override
    public String obfClass(String origName, Set<String> noDuplicate, Random rand) {
        return origName;
    }

    @Override
    public String obfName(Set<String> noDuplicate, Desc param, Random rand) {
        return null;
    }
}
