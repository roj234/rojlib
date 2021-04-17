package ilib.gui.mock;

import ilib.ClientProxy;
import ilib.gui.GuiBaseNI;
import ilib.gui.IGui;
import ilib.gui.comp.Component;
import ilib.gui.comp.*;
import ilib.gui.util.ComponentListener;
import ilib.gui.util.PositionProxy;
import roj.io.IOUtil;

import net.minecraft.client.gui.GuiScreen;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static ilib.net.mock.MockingUtil.baseDir;

/**
 * @author solo6975
 * @since 2022/4/2 3:52
 */
// todo GMultiLineTextInput, GTabs1: ratio button + GPage, GFileSelector
public class GuiMockPackets extends GuiBaseNI implements ComponentListener {
	static List<File> rulePacketAdapters;

	public GuiMockPackets(GuiScreen menu) {
		super(-1, -1, Component.TEXTURE);
		this.prevScreen = menu;
		baseDir.mkdir();
		rulePacketAdapters = IOUtil.findAllFiles(baseDir);
	}

	@Override
	public void initGui() {
		super.initGui();
		PositionProxy pp = (PositionProxy) components.get(2).getListener();
		pp.reposition(this);
	}

	@Override
	protected void addComponents() {
		components.add(GText.alignCenterX(this, 10, "\u00a7l修改PacketAdapter", Color.WHITE));

		components.add(new SavedPackets(this, 10, 24, -10, -40));

		PositionProxy pp = new PositionProxy(this, PositionProxy.POSITION_FLEX_X);
		for (int i = 1; i < 4; i++) {
			pp.position(i, 0, -30);
		}

		components.add(new GButtonNP(this, 0, 0, "返回").setMark(1).setListener(pp));
		components.add(new GButtonNP(this, 0, 0, "刷新").setMark(2).setListener(pp));
		components.add(new GButtonNP(this, 0, 0, "新建").setMark(3).setListener(pp));
	}

	@Override
	public void getDynamicTooltip(Component c, List<String> tooltip, int mouseX, int mouseY) {

	}

	@Override
	public void actionPerformed(Component c, int action) {
		if (action == BUTTON_CLICKED) {
			switch (c.getMark()) {
				case 1:
					mc.displayGuiScreen(prevScreen);
					break;
				case 2:
					((SavedPackets) components.get(1)).refresh();
					break;
				case 3:
					GPopup popup = addPopup("输入名字", "", Collections.singletonList("保存"), 10);
					popup.getComponents().set(2, new GTextInput(popup, 4, 16, 150, 10, ".bin"));
					break;
				case 10:
					popup = closePopup("输入名字");
					File f = new File(baseDir, ((GTextInput) popup.getComponents().get(2)).getText());
					try {
						f.createNewFile();
					} catch (IOException e) {
						e.printStackTrace();
					}
					rulePacketAdapters = IOUtil.findAllFiles(baseDir);
					((SavedPackets) components.get(1)).refresh();
					break;
				case 999:
					closePopup(null);
			}
		}
	}

	private static class SavedPackets extends GScrollView implements ComponentListener {
		public SavedPackets(IGui parent, int x, int y, int w, int h) {
			super(parent, x, y, w, h);
			alwaysShow = true;
		}

		@Override
		protected void addElements(int from, int to) {
			List<File> stored = rulePacketAdapters;

			int y = 4;
			for (int i = from; i < to; i++) {
				File arr = stored.get(i);

				components.add(new GButtonNP(this, 6, y, width / 2, 16, arr.getName()).setEnabled(false).setColor(Color.ORANGE));
				components.add(new GButtonNP(this, -80, y, 0, 16, "改").setMark(1));
				components.add(new GButtonNP(this, -48, y, 0, 16, "删").setMark(2));
				y += 20;
			}
		}

		@Override
		protected int getElementCount() {
			return rulePacketAdapters.size();
		}

		@Override
		protected int getElementLength() {
			return 20;
		}

		@Override
		public void actionPerformed(Component c, int action) {
			int id = components.indexOf(c) - reserved;
			if (id >= 0) {
				id = id / 3 + off;

				switch (c.getMark()) {
					case 1:
						ClientProxy.mc.displayGuiScreen(new GuiEditPA(ClientProxy.mc.currentScreen, rulePacketAdapters.get(id)));
						break;
					case 2:
						if (id >= rulePacketAdapters.size()) return;
						rulePacketAdapters.remove(id).delete();
						break;
				}
			}
		}
	}
}
