package roj.io.down;

import roj.ui.CmdUtil;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/2/17 23:37
 */
class Notify implements IProgressHandler {
    final boolean notify;
    boolean errored = false;

    Notify(boolean notify) {
        this.notify = notify;
    }

    @Override
    public void onReturn() {
        if (notify) CmdUtil.success("下载完成");
    }

    @Override
    public void errorCaught() {
        errored = true;
    }

    @Override
    public boolean continueDownload() {
        return !errored;
    }
}
