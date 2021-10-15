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
package ilib.asm.nixim.client;

import ilib.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.GuiUtilRenderComponents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;

import java.util.Iterator;
import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
@Nixim("net.minecraft.client.gui.GuiNewChat")
public abstract class NiximChatGui extends GuiNewChat {
    public NiximChatGui(Minecraft _lvt_1_) {
        super(_lvt_1_);
    }

    @Copy
    static Logger xxLOGGER;

    @Shadow("field_146252_h")
    private List<ChatLine> chatLines;
    @Shadow("field_146253_i")
    private List<ChatLine> drawnChatLines;
    @Shadow("field_146250_j")
    private int scrollPos;
    @Shadow("field_146251_k")
    private boolean isScrolled;
    @Shadow("field_146247_f")
    Minecraft mc;

    @Inject("func_146237_a")
    private void setChatLine(ITextComponent text, int newId, int updateCounter, boolean noHistory) {
        if (newId != 0) {
            this.deleteChatLine(newId);
        }

        int _lvt_5_ = MathHelper.floor((float) this.getChatWidth() / this.getChatScale());
        List<ITextComponent> _lvt_6_ = GuiUtilRenderComponents.splitText(text, _lvt_5_, this.mc.fontRenderer, false, false);
        boolean isOpen = this.getChatOpen();

        ITextComponent _lvt_9_;
        for (Iterator<ITextComponent> it = _lvt_6_.iterator(); it.hasNext(); this.drawnChatLines.add(0, new ChatLine(updateCounter, _lvt_9_, newId))) {
            _lvt_9_ = it.next();
            if (isOpen && this.scrollPos > 0) {
                this.isScrolled = true;
                this.scroll(1);
            }
        }

        while (this.drawnChatLines.size() > Config.chatLength) {
            this.drawnChatLines.remove(this.drawnChatLines.size() - 1);
        }

        if (!noHistory) {
            this.chatLines.add(0, new ChatLine(updateCounter, text, newId));

            while (this.chatLines.size() > Config.chatLength) {
                this.chatLines.remove(this.chatLines.size() - 1);
            }
        }
    }

    @Shadow("func_146234_a")
    public void printChatMessageWithOptionalDeletion(ITextComponent text, int id) {
        this.setChatLine(text, id, this.mc.ingameGUI.getUpdateCounter(), false);

        if (xxLOGGER == null)
            xxLOGGER = LogManager.getLogger("CHAT");

        if (Config.logChat)
            xxLOGGER.info(text.getFormattedText().replaceAll("\r", "\\\\r").replaceAll("\n", "\\\\n"));
    }
}
