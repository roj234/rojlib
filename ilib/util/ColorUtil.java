package ilib.util;

public class ColorUtil {
	public static final float[] table = new float[256];

	static {
		for (int i = 0; i < 256; i++) {
			table[i] = sRGBToLinear(i / 255f);
		}
	}

	public static float sRGBbyteToLinear(int data) {
		return table[data];
	}

	public static float sRGBToLinear(float color) {
		float result;
		if (color <= 0.04045F) {
			result = color / 12.92F;
		} else {
			result = (float) Math.pow(((color + 0.055F) / 1.055F), 2.4);
		}
		return result;
	}

	public static float linearTosRGB(float color) {
		float result;
		if (color <= 0.04045f / 12.92f) {
			result = color * 12.92F;
		} else {
			result = 1.055F * (float) Math.pow(color, 1 / 2.4) - 0.055F;
		}
		return result;
	}
}
