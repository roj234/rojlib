package ilib.gui.mock;

import ilib.gui.GuiBaseNI;
import ilib.gui.IGui;
import ilib.gui.comp.Component;
import ilib.gui.comp.*;
import ilib.gui.util.ComponentListener;
import ilib.gui.util.PositionProxy;
import ilib.net.mock.MockingUtil;

import net.minecraft.client.gui.GuiScreen;

import java.awt.*;
import java.util.List;

import static ilib.net.mock.MockingUtil.mockMods;

/**
 * @author solo6975
 * @since 2022/4/2 3:52
 */
public class GuiMockMods extends GuiBaseNI implements ComponentListener {
	public GuiMockMods(GuiScreen menu) {
		super(-1, -1, Component.TEXTURE);
		this.prevScreen = menu;
	}

	@Override
	public void initGui() {
		super.initGui();
		PositionProxy pp = (PositionProxy) components.get(2).getListener();
		pp.reposition(this);
	}

	@Override
	protected void addComponents() {
		components.add(GText.alignCenterX(this, 10, "\u00a7l自定义发送给服务器的模组 (名称 版本)", Color.WHITE));

		components.add(new ModView(this, 10, 24, -10, -40));

		PositionProxy pp = new PositionProxy(this, PositionProxy.POSITION_FLEX_X);
		for (int i = 1; i < 4; i++) {
			pp.position(i, 0, -30);
		}

		components.add(new GButtonNP(this, 0, 0, "返回").setMark(1).setListener(pp));
		components.add(new GButtonNP(this, 0, 0, "重置").setMark(2).setListener(pp));
		components.add(new GButtonNP(this, 0, 0, "清空").setMark(3).setListener(pp));

		GButton btnAdd = new GButtonNP(this, 4, 4, "加");
		btnAdd.setColor(Color.WHITE).setMark(4);
		components.add(btnAdd);
	}

	@Override
	public void getDynamicTooltip(ilib.gui.comp.Component c, List<String> tooltip, int mouseX, int mouseY) {
		if (c.getMark() == 3) {
			tooltip.add("留空的话也是不做修改哦");
			tooltip.add("毕竟任何FML客户端都至少有三个mod");
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
					MockingUtil.loadModList();
					((ModView) components.get(1)).refresh();
					break;
				case 3:
					mockMods.clear();
					((ModView) components.get(1)).refresh();
					break;
				case 4:
					mockMods.add(new String[] {"", ""});
					((ModView) components.get(1)).refresh();
					break;
			}
		}
	}

	private static class ModView extends GScrollView implements ComponentListener {
		public ModView(IGui parent, int x, int y, int w, int h) {
			super(parent, x, y, w, h);
			alwaysShow = true;
		}

		@Override
		protected void addElements(int from, int to) {
			List<String[]> mods = mockMods;

			int y = 4;
			for (int i = from; i < to; i++) {
				String[] arr = mods.get(i);

				// 随便定位的，没啥深意
				int halfWidth = (width - 26) / 2 - 30;

				components.add(new GTextInput(this, 6, y, halfWidth, 12, arr[0]));
				components.add(new GTextInput(this, halfWidth + 12, y, halfWidth, 12, arr[1]));
				components.add(new GButtonNP(this, halfWidth * 2 + 24, y, 32, 16, "删除"));
				y += 18;
			}
		}

		@Override
		protected int getElementCount() {
			return mockMods.size();
		}

		@Override
		protected int getElementLength() {
			return 18;
		}

		@Override
		public void actionPerformed(Component c, int action) {
			int id = components.indexOf(c) - reserved;
			if (id >= 0) {
				id = id / 3 + off;

				if (action == TEXT_CHANGED) {
					String[] data = mockMods.get(id);
					id = (id - off) * 3 + reserved;
					GTextInput name = (GTextInput) components.get(id);
					GTextInput vers = (GTextInput) components.get(id + 1);
					data[0] = name.getText();
					data[1] = vers.getText();
				} else {
					mockMods.remove(id);
					refresh();
				}
			}
		}
	}
}
