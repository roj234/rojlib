/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package roj.ui;

import roj.NativeLibrary;
import roj.collect.ToIntMap;
import roj.text.TextUtil;
import roj.util.NativeException;
import roj.util.OS;

import java.awt.event.*;

/**
 * Native functions to register combo keys
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/10/14 22:35
 */
public final class ComboKeys {
    /**
     * key->keycode
     */
    private static final ToIntMap<String> keycodeMap;

    static {
        if (OS.CURRENT == OS.WINDOWS && NativeLibrary.inited) {
            inst = new ComboKeys();
        }

        ToIntMap<String> s2c = new ToIntMap<>();
        s2c.putInt("caps_lock", KeyEvent.VK_CAPS_LOCK);
        s2c.putInt("num_lock", KeyEvent.VK_NUM_LOCK);
        s2c.putInt("scroll_lock", KeyEvent.VK_SCROLL_LOCK);

        s2c.putInt("typed", KeyEvent.KEY_TYPED);
        s2c.putInt("pressed", KeyEvent.KEY_PRESSED);
        s2c.putInt("released", KeyEvent.KEY_RELEASED);

        s2c.putInt("escape", KeyEvent.VK_ESCAPE);
        s2c.putInt("enter", 13);
        s2c.putInt("backspace", KeyEvent.VK_BACK_SPACE);

        s2c.putInt("page_up", KeyEvent.VK_PAGE_UP);
        s2c.putInt("page_down", KeyEvent.VK_PAGE_DOWN);
        s2c.putInt("end", KeyEvent.VK_END);
        s2c.putInt("home", KeyEvent.VK_HOME);
        s2c.putInt("printscreen", 44);
        s2c.putInt("insert", 45);
        s2c.putInt("delete", 46);
        s2c.putInt("help", 47);

        s2c.putInt("left", KeyEvent.VK_LEFT);
        s2c.putInt("up", KeyEvent.VK_UP);
        s2c.putInt("right", KeyEvent.VK_RIGHT);
        s2c.putInt("down", KeyEvent.VK_DOWN);

        s2c.putInt("numpad0", KeyEvent.VK_NUMPAD0);
        s2c.putInt("numpad1", KeyEvent.VK_NUMPAD1);
        s2c.putInt("numpad2", KeyEvent.VK_NUMPAD2);
        s2c.putInt("numpad3", KeyEvent.VK_NUMPAD3);
        s2c.putInt("numpad4", KeyEvent.VK_NUMPAD4);
        s2c.putInt("numpad5", KeyEvent.VK_NUMPAD5);
        s2c.putInt("numpad6", KeyEvent.VK_NUMPAD6);
        s2c.putInt("numpad7", KeyEvent.VK_NUMPAD7);
        s2c.putInt("numpad8", KeyEvent.VK_NUMPAD8);
        s2c.putInt("numpad9", KeyEvent.VK_NUMPAD9);

        s2c.putInt("f1", KeyEvent.VK_F1);
        s2c.putInt("f2", KeyEvent.VK_F2);
        s2c.putInt("f3", KeyEvent.VK_F3);
        s2c.putInt("f4", KeyEvent.VK_F4);
        s2c.putInt("f5", KeyEvent.VK_F5);
        s2c.putInt("f6", KeyEvent.VK_F6);
        s2c.putInt("f7", KeyEvent.VK_F7);
        s2c.putInt("f8", KeyEvent.VK_F8);
        s2c.putInt("f9", KeyEvent.VK_F9);
        s2c.putInt("f10", KeyEvent.VK_F10);
        s2c.putInt("f11", KeyEvent.VK_F11);
        s2c.putInt("f12", KeyEvent.VK_F12);

        s2c.putInt("context_menu", KeyEvent.VK_CONTEXT_MENU);
        keycodeMap = s2c;
    }

    public static boolean available() {
        return inst != null;
    }

    public static ComboKeys getInstance() {
        return inst;
    }

    // Modifier
    public static final int M_ALT     = 1;
    public static final int M_CTRL    = 2;
    public static final int M_SHIFT   = 4;
    public static final int M_WINDOWS = 8;

    // AppCommand Virtual Keys
    public interface AppCommands {
        int AC_BROWSER_BACKWARD    = 1;
        int AC_BROWSER_FORWARD     = 2;
        int AC_BROWSER_REFRESH     = 3;
        int AC_BROWSER_STOP        = 4;
        int AC_BROWSER_SEARCH      = 5;
        int AC_BROWSER_FAVOURITES  = 6;
        int AC_BROWSER_HOME        = 7;
        int AC_VOL_MUTE            = 8;
        int AC_VOL_DOWN            = 9;
        int AC_VOL_UP              = 10;
        int AC_MEDIA_NEXTTRACK     = 11;
        int AC_MEDIA_PREVIOUSTRACK = 12;
        int AC_MEDIA_STOP          = 13;
        int AC_MEDIA_PLAY_PAUSE    = 14;
        int AC_LAUNCH_MAIL         = 15;
        int AC_LAUNCH_MEDIA_SELECT = 16;
        int AC_LAUNCH_APP1         = 17;
        int AC_LAUNCH_APP2         = 18;
        int AC_BASS_DOWN           = 19;
        int AC_BASS_BOOST          = 20;
        int AC_BASS_UP             = 21;
        int AC_TREBLE_DOWN         = 22;
        int AC_TREBLE_UP           = 23;
        int AC_MICROPHONE_VOL_MUTE = 24;
        int AC_MICROPHONE_VOL_DOWN = 25;
        int AC_MICROPHONE_VOL_UP   = 26;
        int AC_HELP                = 27;
        int AC_FIND                = 28;
        int AC_NEW                 = 29;
        int AC_OPEN                = 30;
        int AC_CLOSE               = 31;
        int AC_SAVE                = 32;
        int AC_PRINT               = 33;
        int AC_UNDO                = 34;
        int AC_REDO                = 35;
        int AC_COPY                = 36;
        int AC_CUT                 = 37;
        int AC_PASTE               = 38;
        int AC_REPLY_MAIL          = 39;
        int AC_FORWARD_MAIL        = 40;
        int AC_SEND_MAIL           = 41;
        int AC_SPELL_CHECK         = 42;
        int AC_DICTATE             = 43;
        int AC_MIC_ON_OFF          = 44;
        int AC_CORRECTION_LIST     = 45;
    }

    public static final int HOTKEY_SUPPRESSED  = 1;
    public static final int APPCMD_SUPPRESSED  = 2;
    public static final int ACCEPT_APP_COMMAND = 4;

    /**
     * Singleton when <clinit>
     */
    private static ComboKeys inst;
    private ComboKeys() {
        if (inst != null) throw new RuntimeException();
    }

    public void acceptWM_APPCOMMAND(boolean accept) {
        synchronized (keycodeMap) {
            if (accept) {
                flags |= ACCEPT_APP_COMMAND;
            } else {
                flags &= ~ACCEPT_APP_COMMAND;
            }
        }
    }

    private byte flags;

    private final int[]  hotkeys = new int[32];
    private byte         hotkeyId, hotkeyLen;

    private final byte[] appCmd = new byte[32];
    private byte         appCmdId, appCmdLen;

    public boolean hasHotkeys() {
        synchronized (hotkeys) {
            return hotkeyId < hotkeyLen;
        }
    }

    public boolean hasPendingEvents() {
        synchronized (hotkeys) {
            synchronized (appCmd) {
                return hotkeyId < hotkeyLen || appCmdId < appCmdLen;
            }
        }
    }

    public int poolHotkeys() {
        synchronized (hotkeys) {
            if (hotkeyId >= hotkeyLen) return 0;
            return hotkeys[hotkeyId++];
        }
    }

    public int poolAppCmd() {
        synchronized (appCmd) {
            if (appCmdId >= appCmdLen) return 0;
            return appCmd[appCmdId++] & 0xFF;
        }
    }

    public final long ptr = 0;

    /**
     * example: CTRL+ALT+F3, CTRL+ALT+[
     */
    public void register(int identifier, String key) {
        String[] split = TextUtil.split(key, '+');
        int mask = 0;

        for (int i = split.length - 2; i >= 0; i--) {
            switch (split[i].toUpperCase()) {
                case "ALT":
                    mask |= M_ALT;
                    break;
                case "CTRL":
                case "CONTROL":
                    mask |= M_CTRL;
                    break;
                case "SHIFT":
                    mask |= M_SHIFT;
                    break;
                case "WIN":
                    mask |= M_WINDOWS;
                    break;
            }
        }
        String s = split[split.length - 1];
        int keycode = keycodeMap.getInt(s.toLowerCase());
        if (keycode == -1) {
            if (s.length() == 1) {
                keycode = s.charAt(0);
            } else {
                throw new IllegalArgumentException("Not know keycode for " + s);
            }
        }
        register(identifier, mask, keycode);
    }

    /**
     * Call by JNI
     */
    private void onHotKey(final int identifier) {
        int[] hotkeys = this.hotkeys;
        synchronized (hotkeys) {
            if (hotkeyId > 0) {
                System.arraycopy(hotkeys, hotkeyId, hotkeys, 0, hotkeyLen - hotkeyId);
                hotkeyLen -= hotkeyId;
                hotkeyId = 0;
            }
            if (hotkeyLen < 32) {
                hotkeys[hotkeyLen++] = identifier;
            } else {
                System.arraycopy(hotkeys, 1, hotkeys, 0, 31);
                flags |= HOTKEY_SUPPRESSED;
                hotkeys[31] = identifier;
            }
        }
        synchronized (this) {
            notify();
        }
    }

    /**
     * Call by JNI
     */
    private void onAppCommand(final int appCommand) {
        if ((flags & ACCEPT_APP_COMMAND) == 0) return;
        byte[] cmd = appCmd;
        synchronized (cmd) {
            if (appCmdId > 0) {
                System.arraycopy(cmd, appCmdId, cmd, 0, appCmdLen - appCmdId);
                appCmdLen -= appCmdId;
                appCmdId = 0;
            }
            if (appCmdLen < cmd.length) {
                cmd[appCmdLen++] = (byte) appCommand;
            } else {
                System.arraycopy(cmd, 1, cmd, 0, 31);
                flags |= APPCMD_SUPPRESSED;
                cmd[31] = (byte) appCommand;
            }
        }
        synchronized (this) {
            notify();
        }
    }

    public static native boolean hasWindowNamed(String title);

    public synchronized native void register(int id, int modifier, int keycode) throws NativeException;

    public synchronized native void unregister(int id) throws NativeException;

    public synchronized native void finish() throws NativeException;
}
