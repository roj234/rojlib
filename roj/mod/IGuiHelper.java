package roj.mod;

import roj.ui.UIUtil;

import java.io.IOException;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/2/19 19:59
 */
class IGuiHelper {
    boolean getBoolean(String msg) throws IOException {
        return UIUtil.readBoolean(msg);
    }

    int getNumberInRange(int min, int max) throws IOException {
        return UIUtil.getNumberInRange(min, max);
    }

    String userInput(String msg, boolean optional) throws IOException {
        return UIUtil.userInput(msg);
    }

    void stageInfo(int id, boolean... flags) {}

    boolean isConsole() {
        return true;
    }
}
