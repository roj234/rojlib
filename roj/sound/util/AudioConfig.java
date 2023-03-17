package roj.sound.util;

/**
 * @author solo6975
 * @since 2021/10/2 20:38
 */
public interface AudioConfig {
	int getSamplingRate();

	int channels();

	int getPcmSize();
}
