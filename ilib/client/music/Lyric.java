package ilib.client.music;

/**
 * @author solo6975
 * @since 2022/4/4 14:56
 */
public final class Lyric implements Comparable<Lyric> {
	public int time;
	public String text;

	public Lyric(int time, String text) {
		this.time = time;
		this.text = text;
	}

	@Override
	public int compareTo(Lyric o) {
		return Integer.compare(time, o.time);
	}
}
