package roj.kscript.ast.api;

import roj.kscript.ast.node.LabelNode;
import roj.kscript.ast.node.Node;

import javax.annotation.Nullable;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/10/27 23:17
 */
public final class TryCatchInfo {
    private final LabelNode handler, fin, end;

    public TryCatchInfo(LabelNode handler, LabelNode fin, LabelNode end) {
        this.handler = handler;
        this.fin = fin;
        this.end = end;
    }

    public Node getEnd() {
        return end.next();
    }

    @Nullable
    public Node getFin() {
        return fin.next();
    }

    @Nullable
    public Node getHandler() {
        return handler.next();
    }
}
