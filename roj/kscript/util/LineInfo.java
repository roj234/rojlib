package roj.kscript.util;

import roj.kscript.ast.Node;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author zsy
 * @since 2021/4/17 22:03
 */
public final class LineInfo {
    public int line;
    public Node node;

    @Override
    public String toString() {
        return "{" + node + ": " + line + '}';
    }
}
