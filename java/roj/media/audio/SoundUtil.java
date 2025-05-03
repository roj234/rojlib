package roj.media.audio;

import org.jetbrains.annotations.NotNull;
import roj.util.ByteList;
import roj.util.DynByteBuf;

/**
 * @author solo6975
 * @since 2022/4/3 17:26
 */
public class SoundUtil {
	public static float linear2db(double linear) {
		return (float) (Math.log10(linear <= 0 ? 1e-4 : linear) * 20.0);
	}

	@NotNull
	public static ByteList ConvertPCM(double speed, double outputSampleRate, double inputSampleRate, DynByteBuf inputPCM) {
		double scale = 1/speed;
		double ratio = outputSampleRate / inputSampleRate * scale;
		int outputLength = (int) (inputPCM.readableBytes() / 2 * ratio);

		var compressedPCM2 = new ByteList(outputLength * 2);
		compressedPCM2.wIndex(outputLength * 2);

		// 线性插值
		int chs = 2;
		for (int i = 0; i < outputLength / chs; i++) {
			double pos = i / ratio;
			int index = (int) pos;
			double frac = pos - index;

			for (int ch = 0; ch < chs; ch++) {
				short value;
				int offset = (index * chs + ch) << 1;
				if (inputPCM.readableBytes() - offset > 2) {
					value = (short) (inputPCM.readShort(offset) * (1 - frac) + inputPCM.readShort(offset+2) * frac);
				} else {
					value = inputPCM.readShort(offset);
				}
				compressedPCM2.putShort((i * chs + ch) << 1, value);

			}
		}
		return compressedPCM2;
	}
}
