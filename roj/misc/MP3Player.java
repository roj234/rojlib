package roj.misc;

import roj.io.IOUtil;
import roj.sound.SoundUtil;
import roj.sound.mp3.Header;
import roj.sound.util.FilePlayer;
import roj.sound.util.JavaAudio;
import roj.text.CharList;
import roj.ui.CLIConsole;
import roj.ui.ProgressBar;
import roj.ui.terminal.Argument;
import roj.ui.terminal.CommandConsole;
import roj.ui.terminal.CommandImpl;
import roj.util.ArrayUtil;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static roj.sound.util.FilePlayer.*;
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
		CLIConsole.setConsole(c);

		if (args.length != 2 || !args[1].equals("-notip")) {
			System.out.println("Command: ");
			System.out.println("  mode: single <on/off> repeat <on/off>");
			System.out.println("        shuffle <on/off> vol <vol>");
			System.out.println("        mute");
			System.out.println("  control: cut <index> goto <time>");
			System.out.println("           speed <speed> pause play stop");
			System.out.println("           prev next info list");
		}

		List<File> files = IOUtil.findAllFiles(new File(args[0]), file -> file.getName().toLowerCase().endsWith(".mp3"));
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
					((JavaAudio) play.player.audio).setVolume(SoundUtil.dbSound(ctx.argument("vol", Double.class)));
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
				play.player.pause();
			}));
		c.register(literal("stop")
			.executes(ctx -> {
				play.flags |= WAITING;
				play.player.stop();
			}));
		c.register(literal("play")
			.executes(ctx -> {
				if (play.player.paused()) {
					play.player.pause();
				}
				play.mayNotify();
			}));
		c.register(literal("info")
			.executes(ctx -> {
				play.dumpInfo();
			}));

		final boolean[] muted = {false};
		c.register(literal("mute")
			.executes(ctx -> {
				((JavaAudio) play.player.audio).mute(muted[0] = !muted[0]);
			}));
		c.register(literal("cut")
			.then(argument("id", Argument.number(0, play.playList.size()))
				.executes(ctx -> {
					play.play(ctx.argument("id", Integer.class));
			})));

		CommandImpl search1 = ctx -> {
			String search = ctx.argument("search", String.class);
			System.out.println("===================================");
			List<File> list = Helpers.cast(play.playList);
			for (int i = 0; i < list.size(); i++) {
				File f = list.get(i);
				if (search != null && !f.getName().contains(search)) continue;
				System.out.println("  " + (i + 1) + ". " + f.getPath());
			}
			System.out.println("===================================");
		};

		c.register(literal("list").executes(search1).then(argument("search", Argument.string()).executes(search1)));

		ProgressBar bar = new ProgressBar("播放进度") {
			@Override
			protected void render(CharList b) {
				CLIConsole.renderBottomLine(b, true, 0);
			}
		};
		bar.setUnit("f");
		bar.updateForce(1);

		int prevFrame = 0;
		while (true) {
			try {
				Thread.sleep(500);
			} catch (InterruptedException ignored) {}
			Header header = play.player.getHeader();
			float played = header.getFrames()*header.getFrameDuration();
			int len = (int) ((File) play.playList.get(play.playIndex)).length();
			float allTime = (float) len / header.getFrameSize() * header.getFrameDuration();
			int f = header.getFrames();
			bar.update(played/allTime, f-prevFrame);
			prevFrame = f;
			int intPlayed = (int) played;
			int intAllTime = (int) allTime;
			bar.setPrefix(intPlayed/60+":"+intPlayed%60+"/"+intAllTime/60+":"+intAllTime%60);
		}
	}
}
