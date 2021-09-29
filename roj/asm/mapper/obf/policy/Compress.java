package roj.asm.mapper.obf.policy;

import java.util.Random;
import java.util.Set;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/7/18 23:23
 */
public class Compress implements NamingFunction {
    public Compress() {}

    @Override
    public String obfClass(String origName, Set<String> noDuplicate, Random rand) {
        String s = Integer.toString(noDuplicate.size(), 36);
        if(noDuplicate.add(s)) {
            return s;
        } else {
            return "xxx/" + s;
        }
    }

    int id, ls;

    @Override
    public String obfName(Set<String> noDuplicate, String param, Random rand) {
        if(noDuplicate.size() < ls) {
            id = 0;
            ls = noDuplicate.size();
        }

        String s = Integer.toString(noDuplicate.size(), 36);
        if(noDuplicate.add(s)) {
            return s;
        } else {
            return "_" + s;
        }
    }
}
