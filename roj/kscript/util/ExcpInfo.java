package roj.kscript.util;

import roj.kscript.ast.TryNode;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/1/13 22:36
 */
public final class ExcpInfo {
    public static final ExcpInfo NONE = new ExcpInfo(0, null, null);

    public ScriptException e;
    public TryNode info;
    public byte stage;

    public ExcpInfo(int stage, TryNode info, ScriptException ex) {
        this.stage = (byte) stage;
        this.info = info;
        this.e = ex;
    }
}
