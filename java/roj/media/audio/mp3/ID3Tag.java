package roj.media.audio.mp3;

import roj.media.audio.AudioMetadata;
import roj.util.ByteList;
import roj.util.ByteList.Slice;
import roj.util.DynByteBuf;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;

/**
 * 解析MP3文件的ID3 v1/v2 tag的部分信息。<br>
 * ID3 v1: 128-byte，通常位于文件尾。<br>
 * [0-2] 3-byte: ID3 v1标识 ，为'TAG'表示接下来的125字节为ID3 v1的标题等域。<br>
 * [3—32] 30-byte: 标题<br>
 * [33—62] 30-byte: 艺术家<br>
 * [63—92] 30-byte: 专辑名<br>
 * [93—96] 4-byte: 发行年份<br>
 * [97—126] 30-byte: v1.0 -- 注释/附加/备注信息； v1.1 -- 前29字节为注释/附加/备注信息，最后1字节为音轨信息<br>
 * [127] 1-byte : 流派
 * <p>
 * ID3 v2.2/2.3/2.4不同版本的帧结构不同，所以高版本不兼容低版本。详情见官网：<br>
 * <a href="http://www.id3.org/id3v2-00">id3 v2.2</a><br>
 * <a href="http://www.id3.org/id3v2.3.0">id3 v2.3</a><br>
 * <a href="http://www.id3.org/id3v2.4.0-structure">id3 v2.4</a><br>
 */

public class ID3Tag implements AudioMetadata {
	private byte[] pic;

	// ID3v1 & ID3v2
	private String title, artist, album, year;

	// ID3v2
	private String lyrics, coder; // (内嵌的)歌词, 编码软件

	private int version;
	private int exheaderSize;
	private boolean haveID3v2Footer;

	private static final Charset[] TEXT_ENCODING = {charsetOrDefault("GBK", StandardCharsets.ISO_8859_1), StandardCharsets.UTF_16, StandardCharsets.UTF_16BE, StandardCharsets.UTF_8};

	private static Charset charsetOrDefault(String gbk, Charset charset) {
		try {
			return Charset.forName(gbk);
		} catch (UnsupportedCharsetException e) {
			return charset;
		}
	}

	//--------------------------------------------------------------------
	// ID3v1 & ID3v2

	/**
	 * 在控制台打印标签信息。
	 */
	public String toString() {
		StringBuilder sb = new StringBuilder("ID3Tag: ");
		if (lyrics != null) sb.append("\n      [ Lyric] ").append(lyrics).append("\n");
		if (title != null) sb.append("\n      [ Title] ").append(title);
		if (artist != null) sb.append("\n      [Artist] ").append(artist);
		if (album != null) sb.append("\n      [ Album] ").append(album);
		if (year != null) sb.append("\n      [  Year] ").append(year);
		if (pic != null) sb.append("\n      [ Image] ").append(pic.length);

		return sb.toString();
	}

	public String getTitle() { return title; }
	public String getArtist() { return artist; }
	public String getAlbum() { return album; }
	public String getYear() { return year; }
	public String getCoder() { return coder; }
	public boolean hasPicture() { return pic != null; }
	public void getPicture(DynByteBuf buf) { buf.put(pic); }

	/**
	 * 清除标签信息。
	 */
	public void clear() {
		title = artist = album = year = coder = lyrics = null;
		version = exheaderSize = 0;
		haveID3v2Footer = false;
		pic = null;
	}

	// ID3v1 ------------------------------------------------------------------

	/**
	 * 解析ID3 v1标签信息。源数据可用长度不少于128字节。
	 *
	 * @param b 源数据。
	 * @param off 源数据偏移量。
	 */
	public boolean parseID3V1(byte[] b, int off) {
		if (b.length - off < 128 || b[off] != 'T' || b[off + 1] != 'A' || b[off + 2] != 'G') return false;

		ByteList bl = new Slice(b, 3 + off, 125);

		int i = bl.readZeroTerminate(30);
		if (title == null && i != 0) title = new String(b, 3 + off, i, TEXT_ENCODING[0]);

		bl.rIndex = 30;
		i = bl.readZeroTerminate(30);
		if (artist == null && i != 0) artist = new String(b, 30 + 3 + off, i, TEXT_ENCODING[0]);

		bl.rIndex = 60;
		i = bl.readZeroTerminate(30);
		if (album == null && i != 0) album = new String(b, 60 + 3 + off, i, TEXT_ENCODING[0]);

		bl.rIndex = 90;
		i = bl.readZeroTerminate(30);
		if (year == null && i != 0) year = new String(b, 90 + 3 + off, i, TEXT_ENCODING[0]);

		return true;
	}

	// ID3v2 ------------------------------------------------------------------

	/**
	 * 获取ID3 v2标签信息长度。源数据可用长度不少于头信息长度10字节。
	 *
	 * @param b 源数据。
	 * @param off 源数据偏移量。
	 *
	 * @return 标签信息长度，单位“字节”。以下两种情况返回0：
	 * <ul>
	 * <li>如果源数据b偏移量off开始的数据内未检测到ID3 v2标签信息；</li>
	 * <li>如果源数据b的可用长度少于少于头信息长度10字节。</li>
	 * </ul>
	 */
	public int checkID3V2(byte[] b, int off) {
		if (b.length - off < 10 || b[off] != 'I' || b[off + 1] != 'D' || b[off + 2] != '3') return 0;

		version = b[off + 3] & 0xff;

		if (version > 2 && (b[off + 5] & 0x40) != 0) exheaderSize = 1; // 设置为1表示有扩展头

		haveID3v2Footer = (b[off + 5] & 0x10) != 0;
		int size = synchSafeInt(b, off + 6);
		size += 10; // ID3 header:10-byte
		return size;
	}

	/**
	 * 解析ID3 v2标签信息。从源数据b偏移量off开始的数据含ID3 v2头信息的10字节。
	 *
	 * @param b 源数据。
	 * @param off 源数据偏移量。
	 * @param len 源数据长度。
	 */
	public void parseID3V2(byte[] b, int off, int len) {
		int max_size = off + len;
		int pos = off + 10;    //ID3 v2 header:10-byte
		if (exheaderSize == 1) {
			exheaderSize = synchSafeInt(b, off);
			pos += exheaderSize;
		}
		max_size -= 10;        //1 frame header: 10-byte
		if (haveID3v2Footer) max_size -= 10;

		while (pos < max_size) pos += getFrame(b, pos, max_size);
	}

	private static int synchSafeInt(byte[] b, int off) {
		int i = (b[off] & 0x7f) << 21;
		i |= (b[off + 1] & 0x7f) << 14;
		i |= (b[off + 2] & 0x7f) << 7;
		i |= b[off + 3] & 0x7f;
		return i;
	}

	private static int byte2int(byte[] b, int off, int len) {
		int i, ret = b[off] & 0xff;
		for (i = 1; i < len; i++) {
			ret <<= 8;
			ret |= b[off + i] & 0xff;
		}
		return ret;
	}

	private int getFrame(byte[] b, int off, int endPos) {
		int id_part = 4, frame_header = 10;
		if (version == 2) {
			id_part = 3;
			frame_header = 6;
		}
		String id = new String(b, off, id_part, StandardCharsets.US_ASCII);
		off += id_part;        // Frame ID

		int fsize, len;
		if (version <= 3) {
			fsize = len = byte2int(b, off, id_part);//Size  $xx xx xx xx
		} else {
			fsize = len = synchSafeInt(b, off);        //Size 4 * %0xxxxxxx
		}
		if (fsize < 1) return frame_header;

		off += id_part;        // frame size

		if (version > 2) off += 2;        // flag: 2-byte

		int enc = b[off];
		off++; len--; // Text encoding: 1-byte

		if (len <= 0 || off + len > endPos || enc < 0 || enc >= TEXT_ENCODING.length) return fsize + frame_header;

		try {
			switch (id) {
				case "TT2":     // TT2: (ID3 v2.2)标题
				case "TIT2":    //TIT2:  标题
					if (title == null) title = new String(b, off, len, TEXT_ENCODING[enc]);
					break;
				case "TYE":
				case "TYER":    //TYER:  发行年
					if (year == null) year = new String(b, off, len, TEXT_ENCODING[enc]);
					break;
				case "TCON":    //TCON:  流派
					break;
				case "TAL":
				case "TALB":    //TALB:  唱片集
					if (album == null) album = new String(b, off, len, TEXT_ENCODING[enc]);
					break;
				case "TP1":
				case "TPE1":    //TPE1:  艺术家
					if (artist == null) {
						artist = new String(b, off, len, TEXT_ENCODING[enc]);
					}
					break;
				case "TRCK":    //TRCK:  音轨
					break;
				case "TSSE":    //TSSE:  编码软件
					coder = new String(b, off, len, TEXT_ENCODING[enc]);
					break;
				case "USLT":    //USLT:  歌词
					//off += 4;    //Language: 4-byte
					//len -= 4;
					lyrics = new String(b, off, len, TEXT_ENCODING[enc]);
					System.out.println("LYRIC " + lyrics);
					break;
				case "APIC":    //APIC
					//MIME type: "image/png" or "image/jpeg"
					for (id_part = off; b[id_part] != 0 && id_part < endPos; id_part++) ;
					//String MIMEtype = new String(b, off, id_part - off, TEXT_ENCODING[enc]);
					//System.out.println("[APIC MIME type] " + MIMEtype);
					len -= id_part - off + 1;
					off = id_part + 1;
					int picture_type = b[off] & 0xff;
					//System.out.println("[APIC Picture type] " + picture_type);
					off++;    //Picture type
					len--;
					for (id_part = off; b[id_part] != 0 && id_part < endPos; id_part++) ;
					//System.out.println("[APIC Description] "
					//        + new String(b, off, id_part - off, TEXT_ENCODING[enc]));
					len -= id_part - off + 1;
					off = id_part + 1;
					//<text string according to encoding> $00 (00)
					if (b[off] == 0) { //(00)
						len--;
						off++;
					}
					//Picture data (binary data): 从b[off]开始的len字节
					if (pic == null) {
						pic = new byte[len];
						System.arraycopy(b, off, pic, 0, len);
					}
					break;
				default:
					System.out.println("未知TAG: " + id);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return fsize + frame_header;
	}
}