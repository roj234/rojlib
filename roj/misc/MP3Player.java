package roj.misc;

import roj.config.word.Tokenizer;
import roj.config.word.Word;
import roj.io.FileUtil;
import roj.sound.SoundUtil;
import roj.sound.util.FilePlayer;
import roj.sound.util.JavaAudio;
import roj.ui.CmdUtil;
import roj.ui.UIUtil;
import roj.util.ArrayUtil;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static roj.sound.util.FilePlayer.*;

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

		List<File> files = FileUtil.findAllFiles(new File(args[0]), file -> file.getName().toLowerCase().endsWith(".mp3"));
		FilePlayer play = new FilePlayer(files);
		play.start();

		if (args.length != 2 || !args[1].equals("-notip")) {
			System.out.println("Command: ");
			System.out.println("  mode: single <on/off> repeat <on/off>");
			System.out.println("        shuffle <on/off> vol <vol>");
			System.out.println("        mute");
			System.out.println("  control: cut <index> goto <time>");
			System.out.println("           speed <speed> pause play stop");
			System.out.println("           prev next info list");
		}

		Tokenizer tokenizer = new Tokenizer();
		ArrayList<String> tmp = new ArrayList<>(4);
		boolean muted = false;

		o:
		while (true) {
			tokenizer.init(UIUtil.userInput("> "));
			tmp.clear();
			try {
				while (tokenizer.hasNext()) {
					Word w = tokenizer.readWord();
					if (w.type() == Word.EOF) {
						CmdUtil.error("指令有误 " + tmp);
						continue o;
					}
					tmp.add(w.val());
				}
				if (tmp.isEmpty()) continue;
				switch (tmp.get(0)) {
					case "single":
						if (tmp.get(1).equals("on")) {play.flags |= PLAY_SINGLE;} else play.flags &= ~PLAY_SINGLE;
						break;
					case "repeat":
						if (tmp.get(1).equals("on")) {play.flags |= PLAY_REPEAT;} else play.flags &= ~PLAY_REPEAT;
						break;
					case "shuffle":
						if (tmp.get(1).equals("on")) {ArrayUtil.shuffle(play.playList, play.rng);} else {
							play.playList.clear();
							play.playList.addAll(Helpers.cast(play.playListBackup));
						}
						break;
					case "cut":
						play.play(Integer.parseInt(tmp.get(1)) - 1);
						break;
					case "goto":
						if ((play.flags & WAITING) == 0) play.player.cut(Double.parseDouble(tmp.get(1)));
						break;
					case "prev":
						play.play(play.playIndex == 0 ? play.playList.size() - 1 : play.playIndex - 1);
						break;
					case "next":
						play.play(play.playIndex == play.playList.size() - 1 ? 0 : play.playIndex + 1);
						break;
					case "pause":
						play.player.pause();
						break;
					case "stop":
						play.flags |= WAITING;
						play.player.stop();
						break;
					case "play":
						if (play.player.paused()) {
							play.player.pause();
						}
						play.mayNotify();
						break;
					case "info":
						play.dumpInfo();
						break;
					case "speed":
						System.out.println("WIP");
						break;
					case "vol":
						((JavaAudio) play.player.audio).setVolume(SoundUtil.dbSound(Double.parseDouble(tmp.get(1))));
						break;
					case "mute":
						((JavaAudio) play.player.audio).mute(muted = !muted);
						break;
					case "list":
						System.out.println("===================================");
						List<File> list = Helpers.cast(play.playList);
						for (int i = 0; i < list.size(); i++) {
							File f = list.get(i);
							if (tmp.size() > 1 && !f.getName().contains(tmp.get(1))) continue;
							System.out.println("  " + (i + 1) + ". " + f.getPath());
						}
						System.out.println("===================================");
						break;

				}
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
	}
}
