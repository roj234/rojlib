package roj.media.audio;

import roj.collect.MyHashMap;
import roj.io.IOUtil;
import roj.io.MyDataInput;
import roj.reflect.Unaligned;

import java.io.IOException;
import java.util.Map;

/**
 * <a href="https://magiclen.org/mp3-tag/">MP3标签格式(ID3、APE)超详细解析 | MagicLen</a>
 * @author Roj234
 * @since 2024/2/20 0020 0:49
 */
public class APETag implements AudioMetadata {
	public static final long SIGNATURE = 0x4150455441474558L;
	private int itemCount;
	private Map<String, Object> attributes = new MyHashMap<>();

	/**
	 * 获取APE标签信息长度。源数据b的可用长度不少于32字节。
	 */
	public int findFooter(byte[] b, int off) {
		if (b.length - off < 32) return 0;

		// APETAGEX
		if (Unaligned.U.get64UB(b, Unaligned.ARRAY_BYTE_BASE_OFFSET + off) == SIGNATURE) {
			itemCount = Unaligned.U.get32UL(b, Unaligned.ARRAY_BYTE_BASE_OFFSET + off + 16);
			int flags = Unaligned.U.get32UL(b, Unaligned.ARRAY_BYTE_BASE_OFFSET + off + 20);
			// Illegal flag
			if ((flags&0x60) != 0) return 0;
			int len = Unaligned.U.get32UL(b, Unaligned.ARRAY_BYTE_BASE_OFFSET + off + 12);
			return (flags&0x80) != 0 ? len+32 : len;
		}
		return 0;
	}

	public void parseTag(MyDataInput st, boolean noHeader) throws IOException {
		if (!noHeader) {
			if (st.readLong() != SIGNATURE) return;
			int apeVer = st.readIntLE();
			int tagSize = st.readIntLE();
			itemCount = st.readIntLE();
			int tagFlag = st.readInt();
			st.skipForce(8);
			if ((tagFlag&0x20) == 0) return;
		}

		for (int i = 0; i < itemCount; i++) readItem(st);
	}

	private void readItem(MyDataInput st) throws IOException {
		int valueSize = st.readIntLE();
		int flags = st.readInt()&7;

		var tmp = IOUtil.getSharedByteBuf();
		int b;
		while ((b = st.readByte()) != 0) tmp.put(b);

		var key = tmp.readAscii(tmp.readableBytes());

		Object o;
		if ((flags>>1) == 1) o = st.readBytes(valueSize);
		else o = st.readUTF(valueSize);

		attributes.put(key, o);
	}

	public String getTitle() {return (String) attributes.get("Title");}
	public String getArtist() {return (String) attributes.get("Artist");}
	public String getAlbum() {return (String) attributes.get("Album");}
	public Object getNamedAttribute(String name) {return attributes.get(name);}

	public String toString() {return "APETAG: "+getTitle()+" - "+getArtist()+" - "+getAlbum();}
}