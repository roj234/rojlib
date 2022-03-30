package ilib.asm.nixim.mock;

import ilib.client.music.GuiMusic;
import ilib.gui.mock.GuiMock;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.resources.I18n;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.util.EmptyArrays;

import java.io.IOException;

/**
 * @author solo6975
 * @since 2022/4/1 1:31
 */
@Nixim("net.minecraft.client.gui.GuiMainMenu")
public class MockBtn extends GuiMainMenu {
    @Shadow("field_175372_K")
    GuiButton realmsButton;
    @Shadow("modButton")
    GuiButton modButton;

    @Inject("func_73969_a")
    private void addSingleplayerMultiplayerButtons(int y, int unused) {
        Object[] e = EmptyArrays.OBJECTS;
        this.buttonList.add(new GuiButton(1, this.width / 2 - 100, y, I18n.format("menu.singleplayer", e)));
        this.buttonList.add(new GuiButton(2, this.width / 2 - 100, y + unused, I18n.format("menu.multiplayer", e)));
        this.realmsButton = this.addButton(new GuiButton(14, this.width / 2 + 2, y + unused * 2, 98, 20, I18n.format("menu.online", e).replace("Minecraft", "").trim()));
        this.buttonList.add(this.modButton = new GuiButton(6, this.width / 2 - 100, y + unused * 2, 98, 20, I18n.format("fml.menu.mods", e)));

        this.buttonList.add(new GuiButton(20, this.width / 2 - 124, y + 48, 20, 20, "Mock"));
        this.buttonList.add(new GuiButton(21, this.width / 2 - 124, y + 24, 20, 20, "音"));
    }

    @Override
    @Inject("func_146284_a")
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 20:
                this.mc.displayGuiScreen(new GuiMock(this));
                break;
            case 21:
                this.mc.displayGuiScreen(new GuiMusic(this));
                break;
            default:
                super.actionPerformed(button);
                break;
        }
    }
}
