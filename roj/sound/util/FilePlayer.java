package roj.sound.util;

import roj.collect.SimpleList;
import roj.io.source.FileSource;
import roj.io.source.MemorySource;
import roj.io.source.Source;
import roj.sound.mp3.Player;
import roj.util.ByteList;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.LockSupport;

/**
 * @author solo6975
 * @since 2022/4/3 12:37
 */
public class FilePlayer extends Thread {
	public static final int PLAY_SINGLE = 1, PLAY_REPEAT = 2, WAITING = 4, CUT_SONG = 8;

	public int flags;
	public int playIndex;
	public List<?> playList, playListBackup;
	public Random rng;
	public Player player;

	public FilePlayer(List<?> playList) {
		setName("Music Player");
		setDaemon(true);
		this.playList = playList;
		this.playListBackup = new SimpleList<>(playList);
		this.player = new Player(new JavaAudio());
		this.rng = new Random();
	}

	@Override
	public void run() {
		flags |= WAITING;
		LockSupport.park();
		while ((flags & WAITING) == 0) {
			Object o = playList.get(playIndex);
			//System.out.println("正在播放 " + file.getName());
			tick();

			Source source = null;
			try {
				if (o instanceof ByteList) {source = new MemorySource((ByteList) o);} else if (o instanceof File) {
					source = new FileSource((File) o);
				} else {
					source = (Source) o;
				}
				player.open(source);
				player.decode();
				player.close();
			} catch (IOException e) {
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

	public void tick() {}

	public boolean isPlaying() {
		return (flags & WAITING) == 0;
	}

	public void play(int song) {
		playIndex = song;
		flags |= CUT_SONG;
		player.stop();
		mayNotify();
	}

	public void stopPlaying() {
		player.stop();
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
		System.out.println(player.getHeader());
		System.out.println(player.getID3Tag());
		System.out.println("已播放 " + player.getHeader().getElapse() + "s");
		System.out.println();
	}
}
