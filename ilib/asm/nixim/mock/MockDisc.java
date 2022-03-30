package ilib.asm.nixim.mock;

import ilib.net.mock.MockingUtil;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.ITextComponent;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import java.util.List;

/**
 * @author solo6975
 * @since 2022/4/2 5:00
 */
@Nixim("net.minecraft.client.gui.GuiDisconnected")
class MockDisc extends GuiDisconnected {
    @Shadow("field_146307_h")
    GuiScreen parentScreen;
    @Shadow("field_146304_f")
    ITextComponent message;
    @Copy
    List<String[]> mocked;

    public MockDisc(GuiScreen _lvt_1_, String _lvt_2_, ITextComponent _lvt_3_) {
        super(_lvt_1_, _lvt_2_, _lvt_3_);
    }

    @Inject("func_73866_w_")
    public void initGui() {
        super.initGui();
        mocked = MockingUtil.mockModsFromServerReject(message);
        if (mocked != null)
        this.buttonList.add(new GuiButton(20, 20, height - 20, "替换客户端模组列表(请把名字换成id)"));
    }

    @Inject("func_146284_a")
    protected void actionPerformed(GuiButton _lvt_1_) {
        if (_lvt_1_.id == 20) MockingUtil.mockMods = mocked;
        this.mc.displayGuiScreen(this.parentScreen);
    }
}
