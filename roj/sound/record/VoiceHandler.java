package roj.sound.record;

/**
 * @author Roj234
 * @since 2020/12/19 22:56
 */
public interface VoiceHandler {
	void handle(byte[] buffer, int length);
}
