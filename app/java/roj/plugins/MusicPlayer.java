package roj.plugins;

import org.jetbrains.annotations.Nullable;
import roj.audio.*;
import roj.audio.mp3.MP3Decoder;
import roj.collect.ArrayList;
import roj.crypt.CryptoFactory;
import roj.io.IOUtil;
import roj.io.source.FileSource;
import roj.io.source.Source;
import roj.plugin.Plugin;
import roj.plugin.SimplePlugin;
import roj.reflect.Handles;
import roj.text.CharList;
import roj.ui.Argument;
import roj.ui.Command;
import roj.ui.ProgressBar;
import roj.ui.Tty;
import roj.util.ArrayUtil;
import roj.util.Helpers;

import java.io.File;
import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

import static roj.ui.CommandNode.argument;
import static roj.ui.CommandNode.literal;

/**
 * @author Roj233
 * @since 2021/8/18 13:35
 */
@SimplePlugin(id = "musicPlayer", version = "2.1.1", desc = "音乐播放测试", inheritConfig = true)
public class MusicPlayer extends Plugin implements Runnable {
	private static final VarHandle FLAG = Handles.getInstance().findVarHandle(MusicPlayer.class, "flag", int.class);
	private static final int STOP = 1, SKIP_AUTO = 2;
	private static final int END_STOP = 1, END_NEXT = 2, IS_RANDOM = 4;

	private int flag, mode = END_NEXT;

	private int playIndex;
	private List<File> playList, initList;

	private final SpeakerSink audio = new SpeakerSink();
	private AudioDecoder decoder;
	private AudioMetadata metadata;

	private Thread player;
	private final ProgressBar progress = new ProgressBar("") {
		@Override
		protected void render(CharList b) {
			Tty.renderBottomLine(b, true, 0);}
	};

	@Override
	protected void onEnable() throws Exception {
		String path = getConfig().getString("music_path", "D:\\Music");
		initList = IOUtil.listFiles(new File(path), file -> {
			String ext = IOUtil.extensionName(file.getName());
			return ext.equals("mp3") || ext.equals("wav")/* || ext.equals("ogg") || ext.equals("flac")*/;
		});
		playList = new ArrayList<>(initList);
		flag = STOP;
		var t = new Thread(this, "音乐播放器");
		t.setDaemon(true);
		t.setPriority(Thread.MAX_PRIORITY);
		t.start();
		player = t;

		getScheduler().loop(() -> {
			var dec = getDecoderIfPlaying();
			if (dec != null) {
				double played = dec.getCurrentTime(), duration = dec.getDuration();
				int iPlayed = (int) played, iDuration = (int) duration;

				String name = null;
				var meta = metadata;
				if (meta != null && meta.getTitle() != null) name = meta.getArtist() == null ? meta.getTitle()+" - 佚名" : meta.getTitle()+" - "+meta.getArtist();
				if (name == null) name = IOUtil.fileName(playList.get(playIndex).getName());

				int displayWidth = 40;

				int prefix = 0, prefixLen = 0;
				for (; prefix < name.length(); prefix++) {
					int w = Tty.getCharWidth(name.charAt(prefix));
					if (prefixLen + w > displayWidth) break;
					prefixLen += w;
				}

				finish:
				if (prefix < name.length()) {
					int length = name.length();
					int realLength = length - prefix;

					int i = iPlayed % (realLength * 2);
					if (i >= realLength) i = realLength*2 - i - 1;

					int j = i;
					int tmp = 0;
					while (j < name.length()) {
						int w = Tty.getCharWidth(name.charAt(j++));
						if ((tmp += w) > displayWidth) {
							name = name.substring(i, j);
							break finish;
						}
					}
				}

				progress.setName(name);
				var sb = IOUtil.getSharedCharBuf();
				sb.padNumber(iPlayed/60, 2).append(':')
				  .padNumber(iPlayed%60, 2).append('/')
				  .padNumber(iDuration/60, 2).append(':')
				  .padNumber(iDuration%60, 2);
				progress.setPrefix(sb.toString());
				progress.setProgress(played/duration);
			} else {
				progress.end();
			}
		}, 1000);

		var c = literal("music");
		c.then(literal("mode").then(argument("模式", Argument.string("one", "single", "repeat", "random")).executes(ctx -> {
			var prevMode = mode;
			 switch (ctx.argument("模式", String.class)) {
				 case "random" -> {
					 ArrayUtil.shuffle(playList, CryptoFactory.L64W64X128MixRandom());
					 mode = END_NEXT|IS_RANDOM;
					 return;
				 }
				 case "one" -> mode = END_STOP;
				 case "single" -> mode = 0;
				 case "repeat" -> mode = END_NEXT;
			 }

			 if ((prevMode&IS_RANDOM) != 0) {
				 playList.clear();
				 playList.addAll(initList);
			 }
		 })));

		final boolean[] mute = {false};
		c.then(literal("mute").executes(ctx -> audio.mute(mute[0] = !mute[0])));
		c.then(literal("vol").then(argument("音量", Argument.real(0, 1)).executes(ctx -> audio.setVolume(SoundUtil.linear2db(ctx.argument("音量", Double.class))))));

		Command prevNext = ctx -> {
			int n = playIndex + (ctx.context.endsWith("next") ? 1 : -1);
			if (n >= playList.size()) n = 0;
			else if (n < 0) n = playList.size()-1;
			play(n);
		};
		c.then(literal("prev").executes(prevNext))
		 .then(literal("next").executes(prevNext));
		c.then(literal("play").executes(ctx -> {
				if (audio.paused()) audio.pause();
				else if (((int)FLAG.getAndSet(this, 0) & STOP) != 0) LockSupport.unpark(player);
				else System.out.println("已经在播放");
			 }).then(argument("歌曲序号", Argument.number(1, playList.size()))
			.executes(ctx -> play(ctx.argument("歌曲序号", Integer.class)-1))));
		c.then(literal("pause").executes(ctx -> {
			if (getDecoderIfPlaying() != null) audio.pause();
		}));
		c.then(literal("stop").executes(ctx -> {
			unpause();
			if (FLAG.compareAndSet(this, 0, SKIP_AUTO|STOP)) decoder.disconnect();
		}));

		c.then(literal("seek").then(argument("时间", Argument.real(0,86400)).executes(ctx -> {
			var dec = getDecoderIfPlaying();
			if (dec == null) {
				Tty.warning("当前没有播放歌曲");return;}
			unpause();
			dec.seek(ctx.argument("时间", Double.class));
		})));

		Command list = ctx -> {
			String search = ctx.argument("search", String.class);
			if (search != null) search = search.toLowerCase();

			System.out.println("===================================");
			List<File> playList = Helpers.cast(this.playList);
			for (int i = 0; i < playList.size(); i++) {
				File f = playList.get(i);
				if (search != null && !f.getName().toLowerCase().contains(search)) continue;
				System.out.println("  " + (i+1)+". "+f.getPath());
			}
			System.out.println("===================================");
		};
		c.then(literal("list").executes(list).then(argument("search", Argument.string()).executes(list)));
		c.then(literal("info").executes(ctx -> {
			var dec = getDecoderIfPlaying();
			if (dec == null) {
				Tty.warning("当前没有播放歌曲");return;}
			System.out.println(dec.getDebugInfo());
			System.out.println(metadata);
		}));

		registerCommand(c);
	}

	private void unpause() {
		if (audio.paused()) audio.pause();
	}

	@Override
	protected void onDisable() {
		var t = player;
		if (t != null) t.interrupt();
		progress.end();
	}

	public void play(int song) {
		unpause();
		playIndex = song;
		int f = (int)FLAG.getAndSet(this, SKIP_AUTO);
		if ((f&STOP) != 0) {
			flag = 0;
			LockSupport.unpark(player);
		} else {
			decoder.disconnect();
		}
	}

	@Nullable
	public AudioDecoder getDecoderIfPlaying() {
		var dec = decoder;
		return dec != null && dec.getState() == AudioDecoder.DECODING ? dec : null;
	}

	@Override
	public void run() {
		while (!Thread.interrupted()) {
			if ((flag & STOP) != 0) {LockSupport.park();continue;}

			var song = playList.get(playIndex);
			String type = IOUtil.extensionName(song.getName());
			switch (type) {
				case "mp3" -> decoder = new MP3Decoder();
				case "wav" -> decoder = new WavDecoder();
				default -> {
					Tty.error("no decoder for "+type);
					flag |= STOP;
					continue;
				}
			}

			Source source = null;
			try {
				source = new FileSource(song);
				metadata = decoder.open(source, true);
				try {
					decoder.connect(audio);
				} finally {
					decoder.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			IOUtil.closeSilently(source);

			if ((flag & SKIP_AUTO) != 0) {flag &= ~SKIP_AUTO;continue;}

			int mode = this.mode;
			if ((mode & END_NEXT) != 0) {
				if (++playIndex == playList.size())
					playIndex = 0;
			}
			if ((mode & END_STOP) != 0) {
				flag |= STOP;
			}
		}
	}
}