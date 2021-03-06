package ilib.gui.mock;

import com.mojang.util.UUIDTypeAdapter;
import ilib.ClientProxy;
import ilib.gui.GuiBaseNI;
import ilib.gui.comp.Component;
import ilib.gui.comp.*;
import ilib.gui.util.ComponentListener;
import ilib.gui.util.PositionProxy;
import ilib.world.saver.WorldSaver;
import roj.text.TextUtil;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Session;

import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static ilib.net.mock.MockingUtil.*;

/**
 * @author solo6975
 * @since 2022/4/1 1:24
 */
public class GuiMock extends GuiBaseNI implements ComponentListener {
    public GuiMock(GuiScreen menu) {
        super(-1, -1, Component.TEXTURE);
        this.prevScreen = menu;
    }

    @Override
    public void initGui() {
        super.initGui();
        PositionProxy pp = (PositionProxy) components.get(13).getListener();
        pp.reposition(this);
    }

    @Override
    protected void addComponents() {
        components.add(GText.alignCenterX(this, 10, "\u00a7lProtocolLib mod alpha 0.1.1", Color.WHITE));

        int y = 50;
        for (int i = 0; i < 6; i++) {
            components.add(new GText(this, 50, y + 2, getMockDesc(i), Color.WHITE));
            int id = i;
            components.add(new GTextInput(this, 120, y, 200, 14, String.valueOf(getMockVal(id))) {
                @Override
                protected void onChange(String value) {
                    setMockVal(id, value);
                }

                @Override
                protected boolean isValidText(String text) {
                    return setMockVal(id, text);
                }
            });
            y += 20;
        }

        PositionProxy pp = new PositionProxy(this, PositionProxy.POSITION_FLEX_X);
        pp.position(1, 0, -30)
          .position(2, 0, -30)
          .position(3, 0, -30)
          .position(7, 0, -30);

        components.add(new GButtonNP(this, 0, 0, "??????").setMark(1).setListener(pp));
        components.add(new GButtonNP(this, 0, 0, "??????").setMark(2).setListener(pp));
        components.add(new GButtonNP(this, 0, 0, "???").setMark(3).setListener(pp));
        components.add(new GButtonNP(this, 0, 0, "?????????").setMark(7).setListener(pp));

        components.add(updateImage(new GButtonNP(this, -30, -30, null).setCheckbox().setMark(4)));
        components.add(updateImage(new GButtonNP(this, -60, -30, null).setCheckbox().setMark(5)));
        components.add(new GButtonNP(this, -30, -60, new ItemStack(Blocks.FURNACE)).setCheckbox().setToggled(mockFMLMarker).setMark(6));
    }

    private Component updateImage(Component c) {
        GButton btn = (GButton) c;
        switch (btn.getMark()) {
            case 4:
                btn.setLabel(new ItemStack(WorldSaver.isEnabled() ? Items.GOLDEN_AXE : Items.STONE_AXE));
                btn.setToggled(WorldSaver.isEnabled());
                break;
            case 5:
                btn.setLabel(new ItemStack(autoMockVersion ? Blocks.PISTON : Blocks.OBSIDIAN));
                btn.setToggled(autoMockVersion);
                break;
        }
        return btn;
    }

    @Override
    public void getDynamicTooltip(Component c, List<String> tooltip, int mouseX, int mouseY) {
        switch (c.getMark()) {
            case 2:
                tooltip.add("????????????????????????ModList");
                break;
            case 4:
                tooltip.add("?????????????????????????????????");
                tooltip.add("?????????????????? //worldSaver ????????????");
                break;
            case 5:
                tooltip.add("???????????????????????????????????????");
                tooltip.add("?????????????????????mod????????????mod (WIP)");
                break;
            case 6:
                tooltip.add("???????????????forge?????????");
                break;
            case 7:
                tooltip.add("??????PacketAdapter");
                break;
        }
    }

    @Override
    public void actionPerformed(Component c, int action) {
        if (action == BUTTON_CLICKED) {
            switch (c.getMark()) {
                case 1:
                    mc.displayGuiScreen(prevScreen);
                    break;
                case 2:
                    mc.displayGuiScreen(new GuiMockMods(this));
                    break;
                case 3:
                    addPopup("???????????????", "?????????????????????????????????????????????????????????", Collections.singletonList("?????????????????????"), 999);
                    break;
                case 4:
                    WorldSaver.toggleEnable();
                    updateImage(c);
                    break;
                case 5:
                    autoMockVersion = !autoMockVersion;
                    updateImage(c);
                    break;
                case 6:
                    mockFMLMarker = !mockFMLMarker;
                    break;
                case 7:
                    mc.displayGuiScreen(new GuiMockPackets(this));
                    break;
                case 999:
                    closePopup(null);
                    break;
            }
        }
    }

    private static String getMockDesc(int i) {
        switch (i) {
            case 0:
                return "????????????";
            case 1:
                return "??????IP";
            case 2:
                return "????????????";
            case 3:
                return "?????????";
            case 4:
                return "UUID";
            case 5:
                return "Token";
        }
        return "??????";
    }

    private static Object getMockVal(int i) {
        switch (i) {
            case 0:
                return mockProtocol;
            case 1:
                return mockIp;
            case 2:
                return mockPort;
            case 3:
                return ClientProxy.mc.getSession().getUsername();
            case 4:
                return ClientProxy.mc.getSession().getPlayerID();
            case 5:
                return ClientProxy.mc.getSession().getToken();
        }
        return "??????";
    }

    private static boolean setMockVal(int i, String val) {
        switch (i) {
            case 0:
                if (TextUtil.isNumber(val) == 0) {
                    mockProtocol = Integer.parseInt(val);
                } else {
                    int id = getVersionNumberFromMCString(val);
                    if (id < 0) return false;
                    mockProtocol = id;
                }
                return true;
            case 1:
                mockIp = val.equals("null") ? null : val;
                return true;
            case 2:
                if (TextUtil.isNumber(val) == 0) {
                    mockPort = Integer.parseInt(val);
                    return true;
                }
                return false;
            case 3:
                String id = UUIDTypeAdapter.fromUUID(UUID.nameUUIDFromBytes(val.getBytes()));
                ClientProxy.mc.session = new Session(val, id, id, "legacy");
                return true;
        }
        return false;
    }
}
