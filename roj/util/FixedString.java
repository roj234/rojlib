package roj.util;

import org.jetbrains.annotations.ApiStatus.Internal;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;

/**
 * 定长字符串 (填充)
 *
 * @author Roj234
 * @version 0.2
 * @since 2021/4/21 22:51
 */
@Internal
public class FixedString {
	public FixedString(int max) {
		this.max = max;
		if ((this.max & ~255) != 0) {
			this.len = 1;
		} else if ((this.max & ~65535) != 0) {
			this.len = 2;
		} else {
			this.len = 4;
		}
	}

	final int max;
	final byte len;

	@Nonnull
	public String read(@Nonnull ByteList r) {
		int sLen = 0;
		String string;
		switch (len) {
			case 1:
				sLen = r.readUnsignedByte();
				break;
			case 2:
				sLen = r.readUnsignedShort();
				break;
			case 4:
				sLen = r.readInt();
				break;
		}
		if (sLen <= 0) {
			string = "";
			sLen = 0;
		} else {
			byte[] bytes = r.readBytes(sLen);
			string = new String(bytes, StandardCharsets.UTF_8);
		}
		r.rIndex += this.max - sLen;
		return string;
	}

	public void write(@Nonnull ByteList w, @Nonnull String string) {
		int fullIndex = w.wIndex() + this.len + this.max;
		byte[] arr = string.getBytes(StandardCharsets.UTF_8);
		if (arr.length == 0) {
			w.wIndex(fullIndex);
			return;
		}
		if (arr.length > max) {
			throw new StringIndexOutOfBoundsException(arr.length);
		}
		switch (len) {
			case 1:
				w.put((byte) arr.length);
				break;
			case 2:
				w.putShort(arr.length);
				break;
			case 4:
				w.putInt(arr.length);
				break;
		}
		w.put(arr);
		w.wIndex(fullIndex);
	}

	public int getLength() {
		return this.len + this.max;
	}
}
