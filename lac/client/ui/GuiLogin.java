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
/**
 * This file is a part of more items mod (MIAC)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: GuiLogin.java
 */
package lac.client.ui;

import ilib.ClientProxy;
import ilib.ImpLib;
import ilib.client.GuiHelper;
import lac.client.packets.PacketLogin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import java.io.IOException;

public class GuiLogin extends GuiScreen {
    private String reason = null;

    private final GuiScreen parent;
    private PasswordField   pass;

    public GuiLogin(GuiScreen parent) {
        this.parent = parent;
    }

    public GuiLogin(GuiScreen parent, String reason) {
        this.parent = parent;
        this.reason = reason == null ? null : I18n.format(reason);
    }

    @Override
    public void initGui() {
        final int w = (width / 2) - 100;

        pass = new PasswordField(fontRenderer, w, (int) (height * 0.4), 200, 20);
        pass.setMaxLen(64);
        if (Minecraft.getMinecraft().currentScreen != null) {
            Minecraft.getMinecraft().currentScreen.setFocused(true);
        }

        buttonList.add(new GuiButton(0, width / 2 - 20, (int) (height * 0.7), 40, 20, "OK"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            String passwd = pass.text;
            if (passwd.equals("")) {
                reason = "密码不能为空";
                return;
            }
            this.close();
            PacketLogin.sendToServer(passwd);
            ClientProxy.mc.setIngameFocus();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (pass.textboxKeyTyped(typedChar, keyCode))
            return;
        // ENTER
        ImpLib.logger().debug("" + keyCode);
        if (keyCode == 108) {
            String passwd = pass.text;
            if (passwd.equals("")) {
                reason = "密码不能为空";
                return;
            }
            this.close();
            PacketLogin.sendToServer(passwd);
        }
        // Esc
        if (keyCode == 27) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        pass.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        try {
            drawDefaultBackground();
        } catch (Exception ignored) {}

        if (fontRenderer == null) return;
        drawCenteredString(fontRenderer, "登录", (int) (width * 0.5), (int) (height * 0.1), 0xffff00);
        if (reason != null)
            drawCenteredString(fontRenderer, reason, (int) (width * 0.5), (int) (height * 0.8), 0xff0000);
        pass.drawTextBox();
    }

    public void close() {
        GuiHelper.openClientGui(parent);
    }
}
