package roj.misc;

import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.io.source.FileSource;
import roj.io.source.Source;
import roj.sound.*;
import roj.sound.mp3.MP3Decoder;
import roj.text.CharList;
import roj.ui.CLIUtil;
import roj.ui.ProgressBar;
import roj.ui.terminal.Argument;
import roj.ui.terminal.CommandConsole;
import roj.ui.terminal.CommandImpl;
import roj.util.ArrayUtil;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.LockSupport;

import static roj.misc.MP3Player.FilePlayer.*;
import static roj.ui.terminal.CommandNode.argument;
import static roj.ui.terminal.CommandNode.literal;

/**
 * @author Roj233
 * @since 2021/8/18 13:35
 */
public class MP3Player {

	public static void main(String[] args) throws IOException {
		if (args.length < 1) {
			System.out.println("MP3Player <dir>");
			return;
		}

		CommandConsole c = new CommandConsole("\u001b[33mCLI-MP3\u001b[93m> ");
		CLIUtil.setConsole(c);

		if (args.length != 2 || !args[1].equals("-notip")) {
			System.out.println("Command: ");
			System.out.println("  mode: single <on/off> repeat <on/off>");
			System.out.println("        shuffle <on/off> vol <vol>");
			System.out.println("        mute");
			System.out.println("  control: play [song id] seek <time>");
			System.out.println("           speed <speed> pause stop");
			System.out.println("           prev next info list");
		}

		List<File> files = IOUtil.findAllFiles(new File(args[0]), file -> {
			String ext = IOUtil.extensionName(file.getName());
			return ext.equals("mp3") || ext.equals("wav") || ext.equals("ogg") || ext.equals("flac");
		});
		FilePlayer play = new FilePlayer(files);
		play.start();

		c.register(literal("single")
			.then(argument("on", Argument.bool())
				.executes(ctx -> {
					if (ctx.argument("on", Boolean.class)) play.flags |= PLAY_SINGLE;
					else play.flags &= ~PLAY_SINGLE;
				})));
		c.register(literal("repeat")
			.then(argument("on", Argument.bool())
				.executes(ctx -> {
					if (ctx.argument("on", Boolean.class)) play.flags |= PLAY_REPEAT;
					else play.flags &= ~PLAY_REPEAT;
				})));
		c.register(literal("shuffle")
			.then(argument("on", Argument.bool())
				.executes(ctx -> {
					if (ctx.argument("on", Boolean.class)) ArrayUtil.shuffle(play.playList, play.rng);
					else {
						play.playList.clear();
						play.playList.addAll(Helpers.cast(play.playListBackup));
					}
				})));
		c.register(literal("vol")
			.then(argument("vol", Argument.real(0, 1))
				.executes(ctx -> {
					play.audio.setVolume(SoundUtil.dbSound(ctx.argument("vol", Double.class)));
				})));
		c.register(literal("prev")
			.executes(ctx -> {
				play.play(play.playIndex == 0 ? play.playList.size() - 1 : play.playIndex - 1);
			}));
		c.register(literal("next")
			.executes(ctx -> {
				play.play(play.playIndex == play.playList.size() - 1 ? 0 : play.playIndex + 1);
			}));
		c.register(literal("pause")
			.executes(ctx -> {
				play.audio.pause();
			}));
		c.register(literal("stop")
			.executes(ctx -> {
				play.flags |= WAITING;
				play.decoder.stop();
			}));
		c.register(literal("play")
			.executes(ctx -> {
				if (play.audio.paused()) play.audio.pause();
				else play.mayNotify();
			})
			.then(argument("id", Argument.number(1, play.playList.size()))
				.executes(ctx -> {
					play.play(ctx.argument("id", Integer.class)-1);
				})));
		c.register(literal("info")
			.executes(ctx -> {
				play.dumpInfo();
			}));

		final boolean[] muted = {false};
		c.register(literal("mute")
			.executes(ctx -> {
				play.audio.mute(muted[0] = !muted[0]);
			}));
		c.register(literal("seek")
			.then(argument("id", Argument.real(0,99999))
				.executes(ctx -> {
					play.decoder.seek(ctx.argument("id", Double.class));
			})));

		CommandImpl search1 = ctx -> {
			String search = ctx.argument("search", String.class);
			System.out.println("===================================");
			List<File> list = Helpers.cast(play.playList);
			for (int i = 0; i < list.size(); i++) {
				File f = list.get(i);
				if (search != null && !f.getName().toLowerCase().contains(search.toLowerCase())) continue;
				System.out.println("  " + (i + 1) + ". " + f.getPath());
			}
			System.out.println("===================================");
		};

		c.register(literal("list").executes(search1).then(argument("search", Argument.string()).executes(search1)));

		ProgressBar bar = new ProgressBar("") {
			@Override
			protected void render(CharList b) {
				CLIUtil.renderBottomLine(b, true, 0);
			}
		};
		bar.setHideSpeed(true);

		while (true) {
			double played = play.decoder.getCurrentTime();
			double allTime = play.decoder.getDuration();
			int intPlayed = (int) played;
			int intAllTime = (int) allTime;
			String name = null;
			AudioMetadata meta = play.metadata;
			if (meta != null && meta.getTitle() != null) name = meta.getArtist() == null ? meta.getTitle()+" - 佚名" : meta.getTitle()+" - "+meta.getArtist();
			if (name == null) name = IOUtil.fileName(play.playList.get(play.playIndex).getName());
			bar.setName(name);
			bar.setPrefix(intPlayed/60+":"+intPlayed%60+"/"+intAllTime/60+":"+intAllTime%60);
			bar.update(played/allTime, 0);

			try {
				Thread.sleep(1000);
			} catch (InterruptedException ignored) {}
		}
	}

	/**
	 * @author solo6975
	 * @since 2022/4/3 12:37
	 */
	public static class FilePlayer extends Thread {
		public static final int PLAY_SINGLE = 1, PLAY_REPEAT = 2, WAITING = 4, CUT_SONG = 8;

		public int flags;
		public int playIndex;
		public List<File> playList, playListBackup;
		public Random rng = new Random();
		public SystemAudioOutput audio = new SystemAudioOutput();
		public AudioDecoder decoder = new WavDecoder();
		private AudioMetadata metadata;

		public FilePlayer(List<File> playList) {
			setName("Music Player");
			setDaemon(true);
			this.playList = playList;
			this.playListBackup = new SimpleList<>(playList);
		}

		@Override
		public void run() {
			flags |= WAITING;
			LockSupport.park();
			while ((flags & WAITING) == 0) {
				File f = playList.get(playIndex);

				Source source = null;
				try {
					String type = f.getName().toLowerCase();
					if (type.endsWith(".mp3")) {
						decoder = new MP3Decoder();
					} else if (type.endsWith(".wav")) {
						decoder = new WavDecoder();
					} else {
						throw new IllegalArgumentException("no decoder for "+type);
					}

					source = new FileSource(f);
					metadata = decoder.open(source, audio, true);
					try {
						decoder.decodeLoop();
					} finally {
						decoder.close();
					}
				} catch (Exception e) {
					e.printStackTrace();
					if (source != null) {
						try {
							source.close();
						} catch (IOException ignored) {}
					}
				}

				// stop
				if ((flags & WAITING) != 0) {
					LockSupport.park();
					continue;
				}

				// cut
				if ((flags & CUT_SONG) != 0) {
					flags &= ~CUT_SONG;
					continue;
				}

				// auto next
				if ((flags & PLAY_SINGLE) != 0) {
					if ((flags & PLAY_REPEAT) == 0) {
						flags |= WAITING;
						LockSupport.park();
					}
				} else {
					if (++playIndex == playList.size()) {
						playIndex = 0;
						if ((flags & PLAY_REPEAT) == 0) {
							flags |= WAITING;
							LockSupport.park();
						}
					}
				}
			}
		}

		public boolean isPlaying() {
			return (flags & WAITING) == 0;
		}

		public void play(int song) {
			playIndex = song;
			flags |= CUT_SONG;
			decoder.stop();
			mayNotify();
		}

		public void stopPlaying() {
			decoder.stop();
			mayNotify();
			flags |= WAITING;
		}

		public void mayNotify() {
			if ((flags & WAITING) != 0) {
				flags &= ~WAITING;
				LockSupport.unpark(this);
			}
		}

		public void dumpInfo() {
			System.out.println(decoder.getDebugInfo());
			System.out.println(metadata);
			System.out.println("已播放 "+ decoder.getCurrentTime()+"/"+ decoder.getDuration()+"s");
			System.out.println();
		}
	}
}