package roj.kscript.util;

import roj.kscript.ast.api.TryCatchInfo;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/1/13 22:36
 */
public final class ExceptionInfo {
    public ScriptException exception;
    public TryCatchInfo info;
    public byte stage;

    public ExceptionInfo(int stage, TryCatchInfo info, ScriptException ex) {
        this.stage = (byte) stage;
        this.info = info;
        this.exception = ex;
    }
}
