package roj.kscript.ast;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * 用途嘛... mark
 *
 * @author Roj233
 * @since 2021/4/22 12:36
 */
public final class FrameStatic extends Context {
    public FrameStatic(Context parent, Context self) {
        super(parent);
        this.vars.putAll(self.vars);
    }
}
