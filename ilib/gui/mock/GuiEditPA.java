package ilib.gui.mock;

import com.google.common.collect.BiMap;
import ilib.gui.GuiBaseNI;
import ilib.gui.IGui;
import ilib.gui.comp.Component;
import ilib.gui.comp.*;
import ilib.gui.util.ComponentListener;
import ilib.net.mock.adapter.RuleBasedPacketAdapter;
import ilib.net.mock.adapter.T_IdRemap;
import ilib.net.mock.adapter.T_Ignore;
import ilib.net.mock.adapter.Target;
import roj.collect.IntMap;
import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.config.word.ITokenizer;
import roj.io.IOUtil;
import roj.text.LineReader;
import roj.util.ByteList;
import roj.util.Helpers;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.Packet;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

/**
 * @author Roj233
 * @since 2022/4/10 13:44
 */
public class GuiEditPA extends GuiBaseNI implements ComponentListener {
	File file;
	RuleBasedPacketAdapter rpa;

	public GuiEditPA(GuiScreen screen, File file) {
		super(-1, -1, Component.TEXTURE);
		this.prevScreen = screen;

		this.file = file;
		rpa = new RuleBasedPacketAdapter();
		if (file.length() > 0) {
			try {
				rpa.deserialize(IOUtil.getSharedByteBuf().readStreamFully(new FileInputStream(file)));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	protected void addComponents() {
		GPage page = new GPage(this, 1, 0, -2, -20);
		components.add(page);
		components.add(new GSlider(this, 0, -20, 100, 16, 0) {
			@Override
			protected void acceptValue(float percent) {
				page.setActive(Math.round(percent * 3));
			}

			@Override
			protected void updateLabel(float percent) {
				switch (Math.round(percent * 3)) {
					case 0:
						label = "登录，入站方向";
						break;
					case 1:
						label = "游玩，入站方向";
						break;
					case 2:
						label = "登录，出站方向";
						break;
					case 3:
						label = "游玩，出站方向";
						break;
				}
			}

			@Override
			protected float validateValue(float percent) {
				return Math.round(percent * 3) / 3f;
			}
		});
		components.add(new GButtonNP(this, 160, -20, 22, 22, "返回").setMark(1));
		components.add(new GButtonNP(this, 100, -20, 22, 22, "解析").setMark(2));
		components.add(new GButtonNP(this, 120, -20, 22, 22, "重置").setMark(3));

		page.append(new RPACategory(this, rpa.stateLoginIn, EnumConnectionState.LOGIN, EnumPacketDirection.CLIENTBOUND));
		page.append(new RPACategory(this, rpa.statePlayIn, EnumConnectionState.PLAY, EnumPacketDirection.CLIENTBOUND));
		page.append(new RPACategory(this, rpa.stateLoginOut, EnumConnectionState.LOGIN, EnumPacketDirection.SERVERBOUND));
		page.append(new RPACategory(this, rpa.statePlayOut, EnumConnectionState.PLAY, EnumPacketDirection.SERVERBOUND));
	}

	public void onGuiClosed() {
		ByteList data = IOUtil.getSharedByteBuf();
		rpa.serialize(data);
		try (FileOutputStream fos = new FileOutputStream(file)) {
			data.writeToStream(fos);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void actionPerformed(Component c, int action) {
		switch (c.getMark()) {
			case 1:
				mc.displayGuiScreen(prevScreen);
				break;
			case 2:
				GPopup popup = addPopup("输入框", "", Collections.singletonList("保存"), 4);
				GTextInput gin = new GTextInput(popup, 4, 16, 150, 20, "");
				gin.setMaxLength(9999999);
				popup.getComponents().set(2, gin);
				break;
			case 3:
				rpa.stateLoginIn.clear();
				rpa.statePlayIn.clear();
				rpa.stateLoginOut.clear();
				rpa.statePlayOut.clear();
				initGui();
				break;
			case 4:
				popup = closePopup("输入框");

				List<RPACategory> cat = Helpers.cast(((GPage) components.get(0)).getComponents());

				for (int i = 0; i < cat.size(); i++) {
					cat.get(i).replacements.clear();
				}

				String data = ((GTextInput) popup.getComponents().get(2)).getText();
				parsePacketCode(data, cat);
				break;
			case 999:
				closePopup(null);
		}
	}

	private static void parsePacketCode(String data, List<RPACategory> cat) {
		MyBitSet mySpec = new MyBitSet();
		mySpec.addAll(ITokenizer.SPECIAL);
		mySpec.remove('.');

		int state = 0, dir = 0, id = 0;
		for (String line : new LineReader(data)) {
			line = line.trim();
			if (line.isEmpty()) continue;

			if (line.contains("PLAY")) {
				state = 2;
				dir = 0;
				continue;
			} else if (line.contains("LOGIN")) {
				state = 1;
				dir = 0;
				continue;
			} else if (line.contains("HANDSHAKING") || line.contains("STATUS")) {
				state = 0;
				dir = 0;
				continue;
			}

			if (state != 0) {
				if (line.contains("SERVERBOUND")) {
					dir = 2;
					id = 0;
					continue;
				} else if (line.contains("CLIENTBOUND")) {
					dir = 1;
					id = 0;
					continue;
				}
				if (dir != 0) {
					int i = 0, prevI = 0;

					while (i < line.length()) {
						int c = line.charAt(i++);
						if (mySpec.contains(c)) {
							String val = line.substring(prevI, i - 1);
							if (val.endsWith("class")) {
								cat.get((dir - 1) * 2 + state - 1).replacements.add(new String[] {val, Integer.toString(id)});
								break;
							}
							prevI = i;
						}
					}

					id++;
				}
			}
		}
	}

	private static class RPACategory extends GScrollView implements ComponentListener {
		private final IntMap<Target> map;

		private final List<Class<?>> packets1122;

		List<String[]> replacements = new SimpleList<>();
		boolean outside;

		public RPACategory(IGui parent, IntMap<Target> map, EnumConnectionState state, EnumPacketDirection dir) {
			super(parent, 1, 0, -2, -20);
			alwaysShow = true;
			this.map = map;

			outside = dir == EnumPacketDirection.SERVERBOUND;

			BiMap<Integer, Class<? extends Packet<?>>> map1 = state.directionMaps.get(dir);
			packets1122 = new SimpleList<>(map1.size());
			for (int i = 0; i < map1.size(); i++) {
				packets1122.add(map1.get(i));
			}
		}

		@Override
		protected void addElements(int from, int to) {
			List<Class<?>> pkts = packets1122;

			int y = 4;
			for (int i = from; i < to; i++) {
				String name = pkts.get(i).getSimpleName();

				int halfWidth = (width - 26) / 2;

				Target t = outside ? map.get(i) : reverseFind(i);
				GButtonNP btn = new GButtonNP(this, halfWidth + 12, y, halfWidth, 16, name);
				if (t != null) {
					components.add(new GButtonNP(this, 6, y, halfWidth, 16, t.toString()).setDummy());
					btn.setFlag(0).setColor(Color.GREEN);
				}
				components.add(btn.setMark(1000 + i));
				y += 20;
			}
		}

		private Target reverseFind(int i) {
			for (IntMap.Entry<Target> entry : map.selfEntrySet()) {
				Target v = entry.getValue();
				if (v instanceof T_IdRemap && ((T_IdRemap) v).toId == i) return v;
			}
			return null;
		}

		@Override
		public void getDynamicTooltip(Component c, List<String> tooltip, int mouseX, int mouseY) {
			if (c.getMark() >= 1000) {
				Class<?> clz = packets1122.get(c.getMark() - 1000);
				tooltip.add("Id: " + (c.getMark() - 1000));
				tooltip.add("Name: " + clz.getSimpleName());
				tooltip.add("Field: ");
				for (Field field : clz.getDeclaredFields()) {
					tooltip.add(field.getType().getSimpleName() + ": " + field.getName());
				}
			}
		}

		@Override
		protected int getElementCount() {
			return packets1122.size();
		}

		@Override
		protected int getElementLength() {
			return 20;
		}

		@Override
		public void actionPerformed(Component c, int action) {
			if (c.getMark() >= 1000) {
				GuiEditPA parent = (GuiEditPA) owner;
				GPopup popup = parent.addPopup("为" + ((GButton) c).getLabel() + "替换的对象?", "", Collections.singletonList("OK"), 998);
				popup.setMark(c.getMark() - 1000).setListener(this);

				ReplacementPopup view = new ReplacementPopup(popup, replacements);
				popup.getComponents().set(2, view);
				view.onInit();
				return;
			}

			switch (c.getMark()) {
				case 998:
					GPopup popup = ((GuiEditPA) owner).closePopup(null);
					int selected = ((ReplacementPopup) popup.getComponents().get(2)).selected;
					if (selected == -1) {
						map.putInt(popup.getMark(), new T_Ignore());
					} else {
						String[] nameid = replacements.remove(selected);
						selected = Integer.parseInt(nameid[1]);
						if (outside) {
							map.putInt(popup.getMark(), new T_IdRemap(popup.getMark(), selected));
						} else {
							map.putInt(selected, new T_IdRemap(selected, popup.getMark()));
						}
						refresh();
					}
					break;
				case 999:
					((GuiEditPA) owner).closePopup(null);
					break;
			}
		}
	}

	private static class ReplacementPopup extends GScrollView implements ComponentListener {
		private final List<String[]> data;
		int selected;

		public ReplacementPopup(IGui parent, List<String[]> data) {
			super(parent, 1, 20, -2, -30);
			this.data = data;
			this.selected = -1;
		}

		@Override
		protected void addElements(int from, int to) {
			List<String[]> data = this.data;

			int y = 4;
			for (int i = from; i < to; i++) {
				GButton btn = new GButtonNP(this, 4, y, width - 16, 14, data.get(i)[0]).setDummy();
				if (i == selected) btn.setToggled(true);
				components.add(btn.setMark(100 + i));
				y += 16;
			}
		}

		@Override
		protected int getElementCount() {
			return data.size();
		}

		@Override
		protected int getElementLength() {
			return 16;
		}

		@Override
		public void actionPerformed(Component c, int action) {
			if (c.getMark() < 100) return;
			selected = c.getMark() - 100;
			for (int i = reserved; i < components.size(); i++) {
				((GButton) components.get(i)).setToggled(false);
			}
			((GButton) c).setToggled(true);
		}
	}
}
