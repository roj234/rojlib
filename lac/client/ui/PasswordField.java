package lac.client.ui;

import roj.text.TextUtil;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.GlStateManager.LogicOp;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.math.MathHelper;

public class PasswordField extends Gui {
    public final FontRenderer fr;
    private final int x, y, w, h;
    public String text = "";
    public int maxLen = 32;
    public int cursorPosition, selectionEnd;
    static final int COLOR = 14737632;

    public PasswordField(FontRenderer fr, int x, int y, int w, int h) {
        this.fr = fr;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public void setText(String textIn) {
        if (textIn.length() > this.maxLen) {
            this.text = textIn.substring(0, this.maxLen);
        } else {
            this.text = textIn;
        }

        this.setCursorPositionEnd();
    }

    public String getSelectedText() {
        int _lvt_1_ = this.cursorPosition < this.selectionEnd ? this.cursorPosition : this.selectionEnd;
        int _lvt_2_ = this.cursorPosition < this.selectionEnd ? this.selectionEnd : this.cursorPosition;
        return this.text.substring(_lvt_1_, _lvt_2_);
    }

    public void writeText(String textToWrite) {
        String _lvt_2_ = "";
        String _lvt_3_ = ChatAllowedCharacters.filterAllowedCharacters(textToWrite);
        int _lvt_4_ = this.cursorPosition < this.selectionEnd ? this.cursorPosition : this.selectionEnd;
        int _lvt_5_ = this.cursorPosition < this.selectionEnd ? this.selectionEnd : this.cursorPosition;
        int _lvt_6_ = this.maxLen - this.text.length() - (_lvt_4_ - _lvt_5_);
        if (!this.text.isEmpty()) {
            _lvt_2_ = _lvt_2_ + this.text.substring(0, _lvt_4_);
        }

        int _lvt_8_;
        if (_lvt_6_ < _lvt_3_.length()) {
            _lvt_2_ = _lvt_2_ + _lvt_3_.substring(0, _lvt_6_);
            _lvt_8_ = _lvt_6_;
        } else {
            _lvt_2_ = _lvt_2_ + _lvt_3_;
            _lvt_8_ = _lvt_3_.length();
        }

        if (!this.text.isEmpty() && _lvt_5_ < this.text.length()) {
            _lvt_2_ = _lvt_2_ + this.text.substring(_lvt_5_);
        }

        this.text = _lvt_2_;
        this.moveCursorBy(_lvt_4_ - this.selectionEnd + _lvt_8_);
    }

    public void deleteWords(int num) {
        if (!this.text.isEmpty()) {
            if (this.selectionEnd != this.cursorPosition) {
                this.writeText("");
            } else {
                this.deleteFromCursor(this.getNthWordFromCursor(num) - this.cursorPosition);
            }
        }
    }

    public void deleteFromCursor(int num) {
        if (!this.text.isEmpty()) {
            if (this.selectionEnd != this.cursorPosition) {
                this.writeText("");
            } else {
                boolean rev = num < 0;
                int s = rev ? this.cursorPosition + num : this.cursorPosition;
                int e = rev ? this.cursorPosition : this.cursorPosition + num;
                String text = "";
                if (s >= 0) {
                    text = this.text.substring(0, s);
                }

                if (e < this.text.length()) {
                    text = text + this.text.substring(e);
                }

                this.text = text;
                if (rev) {
                    this.moveCursorBy(num);
                }
            }
        }
    }

    public int getNthWordFromCursor(int numWords) {
        return this.getNthWordFromPos(numWords, this.cursorPosition);
    }

    public int getNthWordFromPos(int n, int off) {
        return this.getNthWordFromPosWS(n, off, true);
    }

    public int getNthWordFromPosWS(int n, int off, boolean trim) {
        boolean reverse = n < 0;
        n = Math.abs(n);
        for(int i = 0; i < n; ++i) {
            if (!reverse) {
                int len = this.text.length();
                off = this.text.indexOf(32, off);
                if (off == -1) {
                    off = len;
                } else if (trim) {
                    while(off < len && this.text.charAt(off) == ' ') {
                        ++off;
                    }
                }
            } else {
                if (trim)
                while(off > 0 && this.text.charAt(off - 1) == ' ') {
                    --off;
                }

                while(off > 0 && this.text.charAt(off - 1) != ' ') {
                    --off;
                }
            }
        }

        return off;
    }

    public void moveCursorBy(int num) {
        this.setCursorPosition(this.selectionEnd + num);
    }

    public void setCursorPosition(int pos) {
        this.cursorPosition = pos;
        int _lvt_2_ = this.text.length();
        this.cursorPosition = MathHelper.clamp(this.cursorPosition, 0, _lvt_2_);
        this.setSelectionPos(this.cursorPosition);
    }

    public void setCursorPositionZero() {
        this.setCursorPosition(0);
    }

    public void setCursorPositionEnd() {
        this.setCursorPosition(this.text.length());
    }

    public boolean textboxKeyTyped(char _lvt_1_, int keyCode) {
        if (GuiScreen.isKeyComboCtrlA(keyCode)) {
            this.setCursorPositionEnd();
            this.setSelectionPos(0);
            return true;
        } else if (GuiScreen.isKeyComboCtrlC(keyCode)) {
            GuiScreen.setClipboardString(this.getSelectedText());
            return true;
        } else if (GuiScreen.isKeyComboCtrlV(keyCode)) {
            this.writeText(GuiScreen.getClipboardString());

            return true;
        } else if (GuiScreen.isKeyComboCtrlX(keyCode)) {
            GuiScreen.setClipboardString(this.getSelectedText());
            this.writeText("");

            return true;
        } else {
            switch(keyCode) {
                case 14:
                    if (GuiScreen.isCtrlKeyDown()) {
                        this.deleteWords(-1);
                    } else {
                        this.deleteFromCursor(-1);
                    }

                    return true;
                case 199:
                    if (GuiScreen.isShiftKeyDown()) {
                        this.setSelectionPos(0);
                    } else {
                        this.setCursorPositionZero();
                    }

                    return true;
                case 203:
                    if (GuiScreen.isShiftKeyDown()) {
                        if (GuiScreen.isCtrlKeyDown()) {
                            this.setSelectionPos(this.getNthWordFromPos(-1, this.selectionEnd));
                        } else {
                            this.setSelectionPos(this.selectionEnd - 1);
                        }
                    } else if (GuiScreen.isCtrlKeyDown()) {
                        this.setCursorPosition(this.getNthWordFromCursor(-1));
                    } else {
                        this.moveCursorBy(-1);
                    }

                    return true;
                case 205:
                    if (GuiScreen.isShiftKeyDown()) {
                        if (GuiScreen.isCtrlKeyDown()) {
                            this.setSelectionPos(this.getNthWordFromPos(1, this.selectionEnd));
                        } else {
                            this.setSelectionPos(this.selectionEnd + 1);
                        }
                    } else if (GuiScreen.isCtrlKeyDown()) {
                        this.setCursorPosition(this.getNthWordFromCursor(1));
                    } else {
                        this.moveCursorBy(1);
                    }

                    return true;
                case 207:
                    if (GuiScreen.isShiftKeyDown()) {
                        this.setSelectionPos(this.text.length());
                    } else {
                        this.setCursorPositionEnd();
                    }

                    return true;
                case 211:
                    if (GuiScreen.isCtrlKeyDown()) {
                        this.deleteWords(1);
                    } else {
                        this.deleteFromCursor(1);
                    }

                    return true;
                default:
                    if (ChatAllowedCharacters.isAllowedCharacter(_lvt_1_)) {
                        this.writeText(Character.toString(_lvt_1_));

                        return true;
                    } else {
                        return false;
                    }
            }
        }
    }

    public boolean mouseClicked(int x, int y, int mouseButton) {
        boolean inRegion = x >= this.x && x < this.x + this.w && y >= this.y && y < this.y + this.h;
        if (inRegion && mouseButton == 0) {
            int Dx = x - this.x - 4;

            String str = this.fr.trimStringToWidth(this.text, this.getWidth());
            this.setCursorPosition(this.fr.trimStringToWidth(str, Dx).length());
            return true;
        } else {
            return false;
        }
    }

    public void drawTextBox() {
        drawRect(this.x - 1, this.y - 1, this.x + this.w + 1, this.y + this.h + 1, -6250336);
        drawRect(this.x, this.y, this.x + this.w, this.y + this.h, -16777216);

        int pos = this.cursorPosition;
        int selEnd = this.selectionEnd;
        String display = this.fr.trimStringToWidth(this.text, this.getWidth());
        boolean cursorIn = pos >= 0 && pos <= display.length();
        boolean showCursor = (System.currentTimeMillis() / 333 & 1) == 0 && cursorIn;
        int ofx = this.x + 4;
        int ofy = this.y + (this.h - 8) / 2;
        int ofx1 = ofx;
        if (selEnd > display.length()) {
            selEnd = display.length();
        }

        if (!display.isEmpty()) {
            String hl = TextUtil.repeat(cursorIn ? pos : display.length(), '*').toString();//cursorIn ? display.substring(0, pos) : display;
            ofx1 = this.fr.drawStringWithShadow(hl, (float)ofx, (float)ofy, COLOR);
        }

        boolean bool1 = this.cursorPosition < this.text.length() || this.text.length() >= maxLen;
        int ofx2;
        if (!cursorIn) {
            ofx2 = pos > 0 ? ofx + this.w : ofx;
        } else if (bool1) {
            ofx2 = --ofx1;
        } else {
            ofx2 = ofx1;
        }

        if (!display.isEmpty() && cursorIn && pos < display.length()) {
            fr.drawStringWithShadow(TextUtil.repeat(pos, '*').toString()/*display.substring(pos)*/, ofx1, ofy, COLOR);
        }

        if (showCursor) {
            if (bool1) {
                Gui.drawRect(ofx2, ofy - 1, ofx2 + 1, ofy + 1 + fr.FONT_HEIGHT, -3092272);
            } else {
                fr.drawStringWithShadow("_", ofx2, ofy, COLOR);
            }
        }

        if (selEnd != pos) {
            int endMax = ofx + this.fr.getStringWidth("*") * selEnd;//display.substring(0, selEnd));
            this.drawSelectionBox(ofx2, ofy - 1, endMax - 1, ofy + 1 + fr.FONT_HEIGHT);
        }

    }

    private void drawSelectionBox(int sX, int sY, int eX, int eZ) {
        int tmp;
        if (sX < eX) {
            tmp = sX;
            sX = eX;
            eX = tmp;
        }

        if (sY < eZ) {
            tmp = sY;
            sY = eZ;
            eZ = tmp;
        }

        if (eX > this.x + this.w) {
            eX = this.x + this.w;
        }

        if (sX > this.x + this.w) {
            sX = this.x + this.w;
        }

        Tessellator ts = Tessellator.getInstance();
        BufferBuilder bb = ts.getBuffer();
        GlStateManager.color(0.0F, 0.0F, 255.0F, 255.0F);
        GlStateManager.disableTexture2D();
        GlStateManager.enableColorLogic();
        GlStateManager.colorLogicOp(LogicOp.OR_REVERSE);
        bb.begin(7, DefaultVertexFormats.POSITION);
        bb.pos(sX, eZ, 0.0D).endVertex();
        bb.pos(eX, eZ, 0.0D).endVertex();
        bb.pos(eX, sY, 0.0D).endVertex();
        bb.pos(sX, sY, 0.0D).endVertex();
        ts.draw();
        GlStateManager.disableColorLogic();
        GlStateManager.enableTexture2D();
    }

    public void setMaxLen(int length) {
        this.maxLen = length;
        if (this.text.length() > length) {
            this.text = this.text.substring(0, length);
        }
    }

    public int getWidth() {
        return this.w - 8;
    }

    public void setSelectionPos(int pos) {
        this.selectionEnd = MathHelper.clamp(pos, 0, text.length());
    }
}
