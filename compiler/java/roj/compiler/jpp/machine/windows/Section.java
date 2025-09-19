package roj.compiler.jpp.machine.windows;

import roj.util.ByteList;

import java.util.EnumSet;

/**
 * @author Roj233
 * @since 2022/1/18 21:03
 */
public class Section implements PESegment {
	public String name;
	public int virtualSize, virtualAddress;
	public int rawDataSize, rawDataOffset;
	public int relocationOffset, lineNumberOffset;
	public char relocationCount, lineNumberCount;
	public int characteristics;

	public long getVirtualAddress() {
		return virtualAddress & 0xFFFFFFFFL;
	}

	public long getVirtualSize() {
		return virtualSize & 0xFFFFFFFFL;
	}

	public long getRawDataSize() {
		return rawDataSize & 0xFFFFFFFFL;
	}

	public long getRawDataOffset() {
		return rawDataOffset & 0xFFFFFFFFL;
	}

	public long getRelocationOffset() {
		return relocationOffset & 0xFFFFFFFFL;
	}

	public long getLineNumberOffset() {
		return lineNumberOffset & 0xFFFFFFFFL;
	}

	public EnumSet<SectionFlag> getCharacteristics() {
		return SectionFlag.getFlags(characteristics);
	}

	@Override
	public void toByteArray(PEFile owner, ByteList w) {
		byte[] data = new byte[8];
		for (int i = 0; i < name.length(); i++) {
			data[i] = (byte) name.charAt(i);
		}
		w.put(data)
		 .putIntLE(virtualSize)
		 .putIntLE(virtualAddress)
		 .putIntLE(rawDataSize)
		 .putIntLE(rawDataOffset)
		 .putIntLE(relocationOffset)
		 .putIntLE(lineNumberOffset)
		 .putShortLE(relocationCount)
		 .putShortLE(lineNumberCount)
		 .putIntLE(characteristics);
	}

	@Override
	@SuppressWarnings("deprecation")
	public void fromByteArray(PEFile owner, ByteList r) {
		byte[] nl = r.readBytes(8);
		int i = 0;
		while (i < 8) {
			if (nl[i] == 0) break;
			i++;
		}
		name = new String(nl, 0, 0, i);
		virtualSize = r.readIntLE();
		virtualAddress = r.readIntLE();
		rawDataSize = r.readIntLE();
		rawDataOffset = r.readIntLE();
		relocationOffset = r.readIntLE();
		lineNumberOffset = r.readIntLE();
		relocationCount = (char) r.readUnsignedShortLE();
		lineNumberCount = (char) r.readUnsignedShortLE();
		characteristics = r.readIntLE();
	}
}