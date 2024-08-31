package roj.media.audio;

/**
 * @author solo6975
 * @since 2022/4/3 17:26
 */
public class SoundUtil {
	public static float dbSound(double linear) {
		return (float) (Math.log10(linear <= 0 ? 1e-4 : linear) * 20.0);
	}
}
