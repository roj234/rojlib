package ilib.client.music;

import ilib.ClientProxy;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.io.source.MemorySource;
import roj.io.source.Source;
import roj.net.URIUtil;
import roj.net.http.HttpRequest;
import roj.sound.SoundUtil;
import roj.sound.util.FilePlayer;
import roj.sound.util.JavaAudio;
import roj.text.LineReader;
import roj.util.Helpers;

import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.SoundCategory;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;

/**
 * @author solo6975
 * @since 2022/4/4 0:18
 */
public class MusicPlayer extends FilePlayer {
	public static MusicPlayer instance = new MusicPlayer();
	public SimpleList<Lyric> lyrics;

	public MusicPlayer() {
		super(Collections.emptyList());
		start();
	}

	@Override
	public void tick() {
		File file = (File) playList.get(playIndex);
		lyrics = null;
		String name = file.getName();
		File lyric = new File(file.getParentFile(), name.substring(0, name.lastIndexOf('.') + 1) + "lrc");
		if (lyric.isFile()) {
			try {
				lyrics = parseLyric(IOUtil.readUTF(lyric));
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
	}

	public String getLyric(int timeMs) {
		SimpleList<Lyric> lrcs = lyrics;
		if (lrcs == null) return "没有歌词";
		int i = lrcs.binarySearch(0, lrcs.size(), new Lyric(timeMs, ""));
		if (i < 0) {
			if ((i = -i - 2) < 0) return null;
		}
		return lrcs.get(i).text;
	}

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (!instance.isAlive()) instance = new MusicPlayer();

		GameSettings set = ClientProxy.mc.gameSettings;
		float records = set.getSoundLevel(SoundCategory.RECORDS);
		float master = set.getSoundLevel(SoundCategory.MASTER);
		JavaAudio audio = (JavaAudio) instance.player.audio;
		try {
			audio.setVolume(SoundUtil.dbSound(master * records));
		} catch (Throwable ignored) {
			// threading issues
		}
	}

	public static SimpleList<Lyric> parseLyric(CharSequence lrc) throws MalformedURLException {
		if (lrc.length() == 0) return null;

		SimpleList<Lyric> lrcs = new SimpleList<>();
		for (String lyric : new LineReader(lrc)) {
			lyric = URIUtil.decodeURI(lyric).trim();
			if (lyric.isEmpty()) continue;

			int j = lyric.indexOf(']');
			if (lyric.charAt(0) != '[' || j <= 0) {
				throw new MalformedURLException("Unknown format " + lyric);
			}

			int timeIdx = lyric.indexOf(':', 1);

			try {
				int time = Integer.parseInt(lyric.substring(1, timeIdx)) * 60000 + (int) (Float.parseFloat(lyric.substring(1 + timeIdx, j)) * 1000);

				lrcs.add(new Lyric(time, lyric.substring(j + 1)));
			} catch (NumberFormatException ignored) {}
		}

		lrcs.sort(null);
		return lrcs;
	}

	public static void playUrl(String url) throws IOException {
		HttpRequest.nts().url(new URL(url)).execute().await((shc) -> {
			if (!shc.isSuccess()) return;
			try {
				System.out.println("download " + url + " ok");
				play(new MemorySource(shc.bytes()));
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
	}

	public static void play(Source source) {
		instance.playList.clear();
		instance.playList.add(Helpers.cast(source));
		instance.play(0);
	}
}
