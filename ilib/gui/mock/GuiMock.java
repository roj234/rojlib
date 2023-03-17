package ilib.gui.mock;

import ilib.ClientProxy;
import ilib.client.music.GuiMusic;
import ilib.gui.GuiBaseNI;
import ilib.gui.GuiHelper;
import ilib.gui.comp.Component;
import ilib.gui.comp.*;
import ilib.gui.util.ComponentListener;
import ilib.gui.util.PositionProxy;
import ilib.world.saver.WorldSaver;
import roj.text.TextUtil;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Session;

import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.awt.*;
import java.util.Collections;
import java.util.List;

import static ilib.net.mock.MockingUtil.*;

/**
 * @author solo6975
 * @since 2022/4/1 1:24
 */
public class GuiMock extends GuiBaseNI implements ComponentListener {
	@SubscribeEvent
	public static void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
		GuiScreen gui = event.getGui();
		if (gui instanceof GuiMainMenu) {
			List<GuiButton> btns = event.getButtonList();
			int y = gui.height / 4 + 48;

			btns.add(new GuiButton(1919, gui.width / 2 - 124, y + 48, 20, 20, "M·F"));
			btns.add(new GuiButton(810, gui.width / 2 - 124, y + 24, 20, 20, "音"));
		}
	}

	@SubscribeEvent
	public static void onButtonClick(GuiScreenEvent.ActionPerformedEvent.Post event) {
		if (event.getGui() instanceof GuiMainMenu) {
			int id = event.getButton().id;
			if (id == 1919) {
				GuiHelper.openClientGui(new GuiMock(event.getGui()));
			} else if (id == 810) {
				GuiHelper.openClientGui(new GuiMusic(event.getGui()));
			}
		}
	}

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
		components.add(GText.alignCenterX(this, 10, "\u00a7lModFaker 1.0.0-alpha", Color.WHITE));

		int y = 50;
		for (int i = 0; i < 6; i++) {
			components.add(new GText(this, 50, y + 2, getMockDesc(i), Color.WHITE));
			int id = i;
			components.add(new GTextInput(this, 120, y, 200, 12, String.valueOf(getMockVal(id))) {
				@Override
				protected void onChange(String value) {
					setMockVal(id, value, false);
				}

				@Override
				protected boolean isValidText(String text) {
					return setMockVal(id, text, true);
				}
			});
			y += 20;
		}

		PositionProxy pp = new PositionProxy(this, PositionProxy.POSITION_FLEX_X);
		pp.position(1, 0, -30).position(2, 0, -30).position(3, 0, -30).position(7, 0, -30);

		components.add(new GButtonNP(this, 0, 0, "保存并退出").setMark(1).setListener(pp));
		components.add(new GButtonNP(this, 0, 0, "模组伪装").setMark(2).setListener(pp));
		components.add(new GButtonNP(this, 0, 0, "注册表伪装").setMark(3).setListener(pp));
		components.add(new GButtonNP(this, 0, 0, "跨版本兼容").setMark(7).setListener(pp));

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
				tooltip.add("修改发给服务器的ModList");
				break;
			case 4:
				tooltip.add("进入服务器自动保存世界");
				tooltip.add("你也可以使用 //worldSaver 手动开关");
				break;
			case 5:
				tooltip.add("自动伪装客户端为服务器版本 (暂未实现)");
				tooltip.add("自动伪装客户端mod为服务器mod");
				break;
			case 6:
				tooltip.add("告诉服务器我是forge客户端");
				break;
			case 7:
				tooltip.add("管理PacketAdapter");
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
					addPopup("正在施工！", "抱歉，这玩意现在还不能见人，再等几年吧", Collections.singletonList("啊这，那算了吧"), 999);
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
			case 0: return "协议版本";
			case 1: return "虚拟IP";
			case 2: return "虚拟端口";
			case 3: return "用户名";
			case 4: return "UUID";
			case 5: return "Token";
		}
		return "未知";
	}

	private static Object getMockVal(int i) {
		switch (i) {
			case 0: return mockProtocol;
			case 1: return mockIp;
			case 2: return mockPort;
			case 3: return ClientProxy.mc.getSession().getUsername();
			case 4: return ClientProxy.mc.getSession().getPlayerID();
			case 5: return ClientProxy.mc.getSession().getToken();
		}
		return "未知";
	}

	private static boolean setMockVal(int i, String val, boolean check) {
		Session prevSession = ClientProxy.mc.getSession();
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
				mockIp = val.equals("null")||val.isEmpty() ? null : val;
				return true;
			case 2:
				if (TextUtil.isNumber(val) == 0) {
					mockPort = Integer.parseInt(val);
					return true;
				}
				return false;
			case 3:
				ClientProxy.mc.session = new Session(val, prevSession.getToken(), prevSession.getPlayerID(), "legacy");
				return true;
			case 4:
				if (val.length() == 36) {
					ClientProxy.mc.session = new Session(prevSession.getUsername(), val, prevSession.getToken(), "legacy");
					return true;
				}
				return false;
			case 5:
				if (val.length() == 36) {
					ClientProxy.mc.session = new Session(prevSession.getUsername(), prevSession.getPlayerID(), val, "legacy");
					return true;
				}
				return false;
		}
		return false;
	}
}
