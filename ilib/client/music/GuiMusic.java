package ilib.client.music;

import ilib.anim.Animation;
import ilib.anim.Keyframe;
import ilib.anim.Keyframes;
import ilib.anim.timing.TFRegistry;
import ilib.client.RenderUtils;
import ilib.gui.GuiBaseNI;
import ilib.gui.IGui;
import ilib.gui.comp.Component;
import ilib.gui.comp.*;
import ilib.gui.util.ComponentListener;
import ilib.gui.util.Direction;
import ilib.gui.util.PositionProxy;
import roj.io.IOUtil;
import roj.sound.mp3.Header;
import roj.sound.mp3.Player;
import roj.sound.util.FilePlayer;
import roj.util.ArrayUtil;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;

import net.minecraftforge.common.MinecraftForge;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;

import static ilib.client.music.MusicPlayer.instance;
import static roj.sound.util.FilePlayer.PLAY_REPEAT;
import static roj.sound.util.FilePlayer.PLAY_SINGLE;

/**
 * @author solo6975
 * @since 2022/4/3 12:04
 */
public class GuiMusic extends GuiBaseNI implements ComponentListener {
	private static final Keyframes xRotateIn;

	static {
		xRotateIn = new Keyframes("RotateInLeft");

		Keyframe kf;
		kf = new Keyframe(0);
		kf.rotate(0, 0, 1, (float) Math.toRadians(-90));
		xRotateIn.add(kf);

		kf = new Keyframe(10);
		xRotateIn.add(kf);
	}

	static List<File> musicList;
	static String filter;

	public GuiMusic(GuiScreen menu) {
		super(-1, -1, Component.TEXTURE);
		this.prevScreen = menu;
		if (musicList == null) {
			musicList = getMusicList();
		}
		filter = "";

		MinecraftForge.EVENT_BUS.register(MusicPlayer.class);
	}

	@Override
	public void initGui() {
		super.initGui();
		PositionProxy pp = (PositionProxy) components.get(3).getListener();
		pp.reposition(this);
	}

	@Override
	protected void addComponents() {
		components.add(GText.alignCenterX(this, 10, "ImpLib音乐播放器", Color.WHITE));

		components.add(new MusicList(this, 10, 24, -10, -54).setDirectionAndInit(Direction.RIGHT));

		components.add(new GTextInput(this, 20, 10, 120, 14, "").setMark(1));

		PositionProxy pp = new PositionProxy(this, PositionProxy.POSITION_FLEX_X);
		for (int i = 1; i <= 5; i++) {
			pp.position(i, 0, -50);
		}

		components.add(new GButtonNP(this, 0, 0, "返回").setMark(1).setListener(pp));
		components.add(new GButtonNP(this, 0, 0, "模式").setMark(2).setListener(pp));
		components.add(new GButtonNP(this, 0, 0, "播放/暂停").setMark(3).setListener(pp));
		components.add(new GButtonNP(this, 0, 0, "打乱").setMark(4).setListener(pp));
		components.add(new GButtonNP(this, 0, 0, "刷新").setMark(5).setListener(pp));
		components.add(new GText(this, 8, height - 18, "", Color.WHITE));
		components.add(new MusicBar());

		Animation rotateIn = new Animation(xRotateIn, TFRegistry.LINEAR);
		rotateIn.setDuration(500);
		rotateIn.play();
		for (int i = 0; i < components.size(); i++) {
			components.get(i).setAnimation(rotateIn);
		}
	}

	@Override
	public void updateScreen() {
		GText musicTime = (GText) components.get(components.size() - 2);

		String text;
		Player p = instance.player;
		if ((p.paused() && System.currentTimeMillis() % 2000 < 1000) || p.closed()) {
			text = "";
		} else {
			Header hdr = p.getHeader();
			int totalTime = (int) ((float) musicList.get(instance.playIndex).length() / hdr.getFrameSize() * hdr.getFrameDuration());
			int time = hdr.getElapse();
			text = time / 60 + ":" + time % 60 + " / " + totalTime / 60 + ":" + totalTime % 60;
		}
		musicTime.setText(text);
	}

	@Override
	public void getDynamicTooltip(Component c, List<String> tooltip, int mouseX, int mouseY) {
		if (c.getMark() == 2) {
			tooltip.add(((instance.flags & PLAY_SINGLE) != 0 ? "单曲" : "列表") + ((instance.flags & PLAY_REPEAT) != 0 ? "循环" : ""));
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public void actionPerformed(Component c, int action) {
		if (action == BUTTON_CLICKED) {
			switch (c.getMark()) {
				case 1:
					mc.displayGuiScreen(prevScreen);
					break;
				case 2:
					instance.flags = (instance.flags + 1) & 3;
					break;
				case 3:
					if (instance.playList.isEmpty()) return;
					instance.player.pause();
					instance.mayNotify();
					break;
				case 4:
					List<File> tmp = (List<File>) instance.playListBackup;
					tmp.clear();
					tmp.addAll(musicList);
					ArrayUtil.shuffle(tmp, new Random());
					updateId(tmp);
					musicList.clear();
					musicList.addAll(tmp);

					((MusicList) components.get(1)).refresh();
					break;
				case 5:
					musicList = getMusicList();
					((MusicList) components.get(1)).refresh();
					break;
			}
		} else {
			if (c.getMark() == 1) {
				GTextInput search = (GTextInput) c;
				filter = search.getText().toLowerCase();
				((MusicList) components.get(1)).refresh();
			}
		}
	}

	private final class MusicBar extends SimpleComponent {
		public MusicBar() {
			super(GuiMusic.this, 60, -16, -10, 8);
		}

		@Override
		public void mouseDown(int x, int y, int button) {
			if (button == 0) scroll(x, y);
		}

		@Override
		public void mouseDrag(int x, int y, int button, long time) {
			if (button == 0) scroll(x, y);
		}

		private void scroll(int x, int y) {
			float percent = (float) (x - xPos) / width;

			Header hdr = instance.player.getHeader();
			float totalTime = (float) musicList.get(instance.playIndex).length() / hdr.getFrameSize() * hdr.getFrameDuration();
			try {
				instance.player.cut(percent * totalTime);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void render(int mouseX, int mouseY) {
			GlStateManager.disableTexture2D();

			RenderUtils.drawRectangle(xPos, yPos, 0, width, height, GScrollView.DEFAULT_BACKGROUND.getRGB());

			Header hdr = instance.player.getHeader();

			float pc;
			if (hdr.getFrameSize() == 0) {
				pc = 0;
			} else {
				int totalFrame = (int) musicList.get(instance.playIndex).length() / hdr.getFrameSize();
				pc = (float) hdr.getFrames() / totalFrame;
			}

			RenderUtils.drawRectangle(xPos, yPos, 1, width * pc, height, GScrollView.DEFAULT_FOREGROUND.getRGB());

			GlStateManager.enableTexture2D();
		}
	}

	private static class MusicList extends GScrollView implements ComponentListener {
		public MusicList(IGui parent, int x, int y, int w, int h) {
			super(parent, x, y, w, h);
			alwaysShow = true;
			setStep(3);
		}

		@Override
		protected void addElements(int from, int to) {
			List<File> musics = musicList;

			int y = 4;
			for (int i = from; i < to; i++) {
				String name = musics.get(i).getName();

				int w1 = (width - 14 - 40);

				GButton song = new GButtonNP(this, 6, y, w1, 16, name).setDummy();
				if (instance.playIndex == i && instance.isPlaying()) song.setToggled(true);
				if (!filter.isEmpty() && !name.toLowerCase().contains(filter)) song.setEnabled(false);
				components.add(song);
				components.add(new GButtonNP(this, -48, y, 32, 16, "删除"));
				y += 20;
			}
		}

		@Override
		protected int getElementCount() {
			return musicList.size();
		}

		@Override
		protected int getElementLength() {
			return 20;
		}

		@Override
		@SuppressWarnings("unchecked")
		public void actionPerformed(Component c, int action) {
			int id = components.indexOf(c) - reserved;
			if (id >= 0) {
				int id1 = id / 2 + off;

				if (action == BUTTON_CLICKED) {
					if ((id & 1) != 0) {
						List<File> tmp = (List<File>) instance.playListBackup;
						tmp.clear();
						tmp.addAll(musicList);
						tmp.remove(id1);
						updateId(tmp);

						musicList.remove(id1);

						refresh();
					} else {
						if (instance.playIndex != id1 || !instance.isPlaying()) {
							instance.play(id1);

							id += reserved;
							for (int i = reserved; i < components.size(); i += 2) {
								((GButton) components.get(i)).setToggled(i == id);
							}
						}
					}
				}
			}
		}
	}

	private static List<File> getMusicList() {
		List<File> files = IOUtil.findAllFiles(new File("music"), file -> file.getName().toLowerCase().endsWith(".mp3"));
		instance.playList = files;
		updateId(files);
		return files;
	}

	private static void updateId(List<File> files) {
		FilePlayer play = instance;
		if (!play.playList.isEmpty()) {
			play.playIndex = files.indexOf(play.playList.get(play.playIndex));
			if (play.playIndex < 0) {
				play.stopPlaying();
				play.playIndex = 0;
			}
		}
	}
}
