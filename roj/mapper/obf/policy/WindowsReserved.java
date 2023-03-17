package roj.mapper.obf.policy;

import java.util.Random;

/**
 * Windows保留名称
 *
 * @author Roj233
 * @since 2021/7/18 19:29
 */
public class WindowsReserved extends SimpleNamer {
	public WindowsReserved() {}

	static final String[] RESERVED = new String[] {"CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6",
												   "LPT7", "LPT8", "LPT9"};

	@Override
	protected String obfName0(Random rand) {
		String choice = RESERVED[rand.nextInt(RESERVED.length)];
		int len = buf.length();
		buf.append(choice);
		for (int i = len; i < len + 3; i++) {
			if (rand.nextBoolean()) buf.set(i, Character.toLowerCase(buf.charAt(i)));
		}
		return buf.toString();
	}
}
