package roj.media.audio.mp3;

import roj.collect.MyHashMap;
import roj.crypt.CRC32s;
import roj.io.IOUtil;
import roj.io.MyDataInput;
import roj.io.source.Source;
import roj.media.audio.AudioMetadata;
import roj.text.CharList;
import roj.text.FastCharset;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * 解析MP3文件的ID3 v1/v2.3 标签
 * <a href="http://www.id3.org/id3v2.3.0">id3 v2.3</a>
 * @author Roj234
 */
public class ID3Tag implements AudioMetadata {
	// ID3v1 & ID3v2
	private String title, artist, album, year;

	// ID3v2
	private String lyrics, coder, picMime, picDesc; // (内嵌的)歌词, 编码软件
	private byte[] pic;
	//$00     Other
	//$01     32x32 pixels 'file icon' (PNG only)
	//$02     Other file icon
	//$03     Cover (front)
	//$04     Cover (back)
	//$05     Leaflet page
	//$06     Media (e.g. lable side of CD)
	//$07     Lead artist/lead performer/soloist
	//$08     Artist/performer
	//$09     Conductor
	//$0A     Band/Orchestra
	//$0B     Composer
	//$0C     Lyricist/text writer
	//$0D     Recording Location
	//$0E     During recording
	//$0F     During performance
	//$10     Movie/video screen capture
	//$11     A bright coloured fish
	//$12     Illustration
	//$13     Band/artist logotype
	//$14     Publisher/Studio logotype
	private byte picType;

	private Map<String, Object> attributes = Collections.emptyMap();

	private boolean hasID3v1;
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
		if (pic != null) sb.append("\n      [ Image] ").append(picMime).append(',').append(picDesc).append(',').append(pic.length).append("bytes");

		return sb.toString();
	}

	public String getTitle() { return title; }
	public String getArtist() { return artist; }
	public String getAlbum() { return album; }
	public String getYear() { return year; }
	public String getCoder() { return coder; }
	public String getLyrics() {return lyrics;}
	public String getPictureMime() {return picMime;}
	public void getPicture(DynByteBuf buf) {if (pic != null) buf.put(pic);}

	public void setTitle(String title) {this.title = title;}
	/**
	 * Split via '/'
	 */
	public void setArtist(String artist) {this.artist = artist;}
	public void setAlbum(String album) {this.album = album;}
	public void setYear(String year) {this.year = year;}
	public void setCoder(String coder) {this.coder = coder;}
	public void setLyrics(String lyrics) {this.lyrics = lyrics;}
	public void setPicture(String mime, byte[] pic) {
		this.picMime = Objects.requireNonNull(mime);
		this.pic = Objects.requireNonNull(pic);
		this.picType = 0;
		this.picDesc = "";
	}
	public void setPicture(String mime, byte[] pic, int picType, String picDesc) {
		this.picMime = Objects.requireNonNull(mime);
		this.pic = Objects.requireNonNull(pic);
		this.picType = (byte) picType;
		this.picDesc = Objects.requireNonNull(picDesc);
	}

	public Map<String, Object> getAttributes() {return attributes;}
	public void setAttribute(String name, Object value) {
		if (attributes.isEmpty()) attributes = new MyHashMap<>();
		attributes.put(name, value);
	}

	public Object getNamedAttribute(String name) {return attributes.get(name);}

	/**
	 * 清除标签信息。
	 */
	public void clear() {
		title = artist = album = year = coder = lyrics = null;
		picMime = picDesc = null;
		pic = null;
		attributes.clear();
		hasID3v1 = false;
	}

	// region ID3v1
	/**
	 * 解析ID3 v1标签信息。输入缓冲区长度不少于128字节。
	 * 30-byte: 标题<br>
	 * 30-byte: 艺术家<br>
	 * 30-byte: 专辑名<br>
	 * 4-byte: 发行年份<br>
	 * 30-byte: 注释 若idx[28] == 0则最后一字节是音轨号<br>
	 * 1-byte : 流派
	 */
	public boolean parseID3V1(byte[] b, int off) {
		if (b.length - off < 128 || b[off] != 'T' || b[off + 1] != 'A' || b[off + 2] != 'G') return false;

		var buf = DynByteBuf.wrap(b, 3 + off, 125);

		int i = buf.readZeroTerminate(30);
		if (title == null && i != 0) title = readAsciiOrGBK(buf, i);

		buf.rIndex = 30;
		i = buf.readZeroTerminate(30);
		if (artist == null && i != 0) artist = readAsciiOrGBK(buf, i);

		buf.rIndex = 60;
		i = buf.readZeroTerminate(30);
		if (album == null && i != 0) album = readAsciiOrGBK(buf, i);

		buf.rIndex = 90;
		i = buf.readZeroTerminate(30);
		if (year == null && i != 0) year = buf.readAscii(i);

		hasID3v1 = true;
		return true;
	}

	private String readAsciiOrGBK(ByteList buf, int i) {
		if (i <= 3) return buf.readAscii(i);
		int cn = 0;
		for (int j = buf.rIndex; j < buf.rIndex+i; j++) {
			int u = buf.getU(j);
			if (u > 0x7F) cn++;
		}
		if (cn * 3 > i) {
			CharList sb = IOUtil.getSharedCharBuf();
			FastCharset.GB18030().decodeFixedOut(buf, i, sb, Integer.MAX_VALUE);
			return sb.toString();
		} else {
			return buf.readAscii(i);
		}
	}
	// endregion
	// region ID3v2
	/**
	 * 获取ID3 v2标签信息长度。源数据可用长度不少于10字节(Header)。
	 * @return 标签信息长度。出错或不存在返回0：
	 */
	public int checkID3V2(DynByteBuf in) throws IOException {
		// ID3
		if (in.readableBytes() < 10 || in.readMedium() != 0x494433) return 0;

		int id3_ver  = in.readUnsignedByte(); // ID3V2.[ver]
		int id3_rev  = in.readUnsignedByte(); // Reversion, always zero
		int id3_flag = in.readUnsignedByte(); // 0bUET00000
		int id3_size = synchSafeInt(in);      // TotalSize, Synchronization Safe Integer

		// 太老了
		if (id3_ver < 3) return 0;
		// 测试头
		if ((id3_flag&0xF) != 0) return 0;

		return id3_size + 10; // ID3头部自身长度
	}

	/**
	 * 解析ID3 v2标签信息。
	 * <a href="https://mutagen-specs.readthedocs.io/en/latest/id3/id3v2.4.0-structure.html#id3v2-header">ID3 tag version 2.4.0 - Main Structure — Mutagen Specs 1.0 documentation</a>
	 */
	public void parseID3V2(MyDataInput in) throws IOException {
		in.skipForce(3);

		int id3_ver  = in.readUnsignedByte(); // ID3V2.[ver]
		int id3_rev  = in.readUnsignedByte(); // Reversion, always zero
		int id3_flag = in.readUnsignedByte(); // 0bUET00000
		int id3_size = synchSafeInt(in);      // TotalSize, Synchronization Safe Integer
		long end = in.position() + id3_size;

		boolean hasCrc = false;
		int frameCrc = 0;

		// 扩展头
		if ((id3_flag & 0x40) != 0) {
			int ext_len = synchSafeInt(in);
			if (id3_ver == 3) {
				int extended_flags = in.readUnsignedShort();
				int padding_size = in.readInt();
				end -= padding_size;
				if ((extended_flags&0x8000) != 0) {
					hasCrc = true;
					frameCrc = in.readInt();
				}
			} else {
				in.skipForce(ext_len);
			}
		}

		// 尾部 v2.4
		if (id3_ver >= 4 && (id3_flag & 0x10) != 0) end -= 10;

		while (in.position() < end && readFrame(in));
	}

	private static int synchSafeInt(MyDataInput in) throws IOException {
		int i = in.readUnsignedByte() << 21;
		i |= in.readUnsignedByte() << 14;
		i |= in.readUnsignedByte() << 7;
		i |= in.readUnsignedByte();
		return i;
	}

	private static String readString(MyDataInput in, int len) throws IOException {return readString(in, in.readUnsignedByte(), len-1);}
	private static String readString(MyDataInput in, int encoding, int len) throws IOException {
		return new String(in.readBytes(len), encoding == 1 ? StandardCharsets.UTF_16 : StandardCharsets.ISO_8859_1);
	}

	private boolean readFrame(MyDataInput in) throws IOException {
		String tag = in.readAscii(4);
		// padding
		if (tag.charAt(0) == 0) return false;

		int len = in.readInt();
		if (len <= 0) return false;
		int flag = in.readUnsignedShort();
		long fsize = in.position() + len;
		try {
			// 三字节的是ID3v2.2的标签
			assert tag.length() == 4;
			switch (tag) {
				case "TIT2" -> title = readString(in, len);  // 标题
				case "TYER" -> year = readString(in, len);   // 发行年
				case "TALB" -> album = readString(in, len);  // 唱片集
				case "TPE1" -> artist = readString(in, len); // 艺术家
				//case "TPE2" -> // 唱片集艺术家
				case "TSSE" -> coder = readString(in, len);         // 编码软件
				case "USLT" -> {    //USLT:  歌词
					int encoding = in.readUnsignedByte();
					int language = in.readMedium();

					var tmp = IOUtil.getSharedByteBuf();
					int b;
					if (encoding == 0) while ((b = in.readByte()) != 0) tmp.put(b);
					else while ((b = in.readUnsignedShort()) != 0) tmp.putShort(b);

					var contDesc = new String(tmp.toByteArray(), encoding == 1 ? StandardCharsets.UTF_16 : StandardCharsets.ISO_8859_1);
					lyrics = readString(in, encoding, (int) (fsize - in.position()));

					System.out.println("[ID3]  Land="+Integer.toHexString(language));
					System.out.println("[ID3]  Desc="+contDesc);
					System.out.println("[ID3] LYRIC="+lyrics);
				}
				case "RVAD" -> {
					// Relative volume adjustment
					// This is a more subjective function than the previous ones. It allows the user to say how much he wants to increase/decrease the volume on each channel while the file is played. The purpose is to be able to align all files to a reference volume, so that you don't have to change the volume constantly. This frame may also be used to balance adjust the audio. If the volume peak levels are known then this could be described with the 'Peak volume right' and 'Peak volume left' field. If Peakvolume is not known these fields could be left zeroed or, if no other data follows, be completely omitted. There may only be one "RVAD" frame in each tag.
					//
					//
					//<Header for 'Relative volume adjustment', ID: "RVAD">
					//Increment/decrement             %00xxxxxx
					//Bits used for volume descr.     $xx
					//Relative volume change, right   $xx xx (xx ...)
					//Relative volume change, left    $xx xx (xx ...)
					//Peak volume right               $xx xx (xx ...)
					//Peak volume left                $xx xx (xx ...)
					//In the increment/decrement field bit 0 is used to indicate the right channel and bit 1 is used to indicate the left channel. 1 is increment and 0 is decrement.
					//
					//The 'bits used for volume description' field is normally $10 (16 bits) for MPEG 2 layer I, II and III and MPEG 2.5. This value may not be $00. The volume is always represented with whole bytes, padded in the beginning (highest bits) when 'bits used for volume description' is not a multiple of eight.
					//
					//This datablock is then optionally followed by a volume definition for the left and right back channels. If this information is appended to the frame the first two channels will be treated as front channels. In the increment/decrement field bit 2 is used to indicate the right back channel and bit 3 for the left back channel.
					//
					//
					//Relative volume change, right back      $xx xx (xx ...)
					//Relative volume change, left back       $xx xx (xx ...)
					//Peak volume right back                  $xx xx (xx ...)
					//Peak volume left back                   $xx xx (xx ...)
					//If the center channel adjustment is present the following is appended to the existing frame, after the left and right back channels. The center channel is represented by bit 4 in the increase/decrease field.
					//
					//
					//Relative volume change, center  $xx xx (xx ...)
					//Peak volume center              $xx xx (xx ...)
					//If the bass channel adjustment is present the following is appended to the existing frame, after the center channel. The bass channel is represented by bit 5 in the increase/decrease field.
					//
					//
					//Relative volume change, bass    $xx xx (xx ...)
					//Peak volume bass                $xx xx (xx ...)
					System.out.println("RVAD");
				}
				case "APIC" -> {    //APIC
					int encoding = in.readUnsignedByte();

					var tmp = IOUtil.getSharedByteBuf();
					int b;
					while ((b = in.readByte()) != 0) tmp.put(b);
					picMime = tmp.readAscii(tmp.readableBytes());

					picType = in.readByte();

					tmp.clear();
					if (encoding == 0) while ((b = in.readByte()) != 0) tmp.put(b);
					else while ((b = in.readUnsignedShort()) != 0) tmp.putShort(b);

					picDesc = new String(tmp.toByteArray(), encoding == 1 ? StandardCharsets.UTF_16 : StandardCharsets.ISO_8859_1);
					pic = in.readBytes((int) (fsize - in.position()));
				}
				default -> {
					Object o = switch (tag.charAt(0)) {
						default -> in.readBytes(len);
						case 'T' -> readString(in, len);
						case 'W' -> URI.create(in.readAscii(len));
					};
					setAttribute(tag, o);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		long delta = fsize - in.position();
		if (delta < 0) return false;
		in.skipForce(delta);
		return true;
	}
	// endregion

	public void writeID3v2(Source source) throws IOException {writeID3v2(source, false);}
	public void writeID3v2(Source source, boolean crc) throws IOException {
		var tmp = IOUtil.getSharedByteBuf();

		source.seek(0);
		source.readFully(tmp, 16);
		int header_size_orig = checkID3V2(tmp);

		// ID3v2.4
		tmp.clear();
		tmp.putMedium(0x494433).putShort(0x0300).put(crc ? 0x40 : 0).putInt(0);
		if (crc) tmp.putInt(10).putShort(0x8000).putInt(0).putInt(0); // CRC32 at 20

		if (title != null) writeTextTag(tmp, "TIT2", title);
		if (year != null) writeTextTag(tmp, "TYER", year);
		if (album != null) writeTextTag(tmp, "TALB", album);
		if (artist != null) writeTextTag(tmp, "TPE1", artist);
		if (coder != null) writeTextTag(tmp, "TSSE", coder);
		for (Map.Entry<String, Object> entry : attributes.entrySet()) {
			if (entry.getValue() instanceof String s) {
				writeTextTag(tmp, entry.getKey(), s);
			} else if (entry.getValue() instanceof URI uri) {
				writeURITag(tmp, entry.getKey(), uri);
			}
		}
		if (lyrics != null) writeUSLT(tmp);
		if (picMime != null) writeAPIC(tmp);

		/*if (offset != 0) {
			// 尾部
			tmp.putMediumLE(0x494433).putShort(0x0400).put(flag).putInt(0);
			writeSynchSafeInt(tmp, tmp.wIndex()-4, tmp.wIndex());
		}*/
		if (crc) {
			var crc32 = CRC32s.once(tmp.list, 24, tmp.wIndex()-24);
			tmp.putInt(20, crc32);
		}

		int len = tmp.wIndex() - 10;
		if (len == 0) tmp.clear();
		else writeSynchSafeInt(tmp, 6, len);

		source.moveSelf(header_size_orig, tmp.wIndex(), source.length() - header_size_orig);
		if (hasID3v1) {
			header_size_orig += 128;
			hasID3v1 = false;
		}
		source.setLength(source.length() - header_size_orig + tmp.wIndex());
		source.seek(0);
		source.write(tmp);
	}

	private void writeAPIC(ByteList tmp) {
		tmp.putAscii("APIC");
		var pos = tmp.wIndex();
		tmp.putInt(0).putShort(0);

		boolean isIso8859_1 = TextUtil.isLatin1(picDesc);
		// encoding=_, description=""
		if (isIso8859_1) {
			tmp.put(0).putAscii(picMime).put(0).put(picType).putAscii(picDesc).put(0);
		} else {
			tmp.put(1).putAscii(picMime).put(0).put(picType).put(0xFEFF).putChars(picDesc).putShort(0);
		}
		tmp.put(pic);

		var length = tmp.wIndex() - pos - 6;
		writeSynchSafeInt(tmp, pos, length);
	}
	private void writeUSLT(ByteList tmp) {
		tmp.putAscii("USLT");
		var pos = tmp.wIndex();
		tmp.putInt(0).putShort(0);

		boolean isIso8859_1 = TextUtil.isLatin1(lyrics);
		// encoding=_, description=""
		if (isIso8859_1) {
			tmp.put(0).put(0).putAscii(lyrics);
		} else {
			tmp.put(1).putShort(0).putShort(0xFEFF).putChars(lyrics);
		}

		var length = tmp.wIndex() - pos - 6;
		writeSynchSafeInt(tmp, pos, length);
	}

	private static void writeTextTag(ByteList tmp, String id, String value) {
		tmp.putAscii(id);
		var pos = tmp.wIndex();
		tmp.putInt(0).putShort(0);

		if (TextUtil.isLatin1(value)) {
			tmp.put(0).putAscii(value);
		} else {
			tmp.put(1).putShort(0xFEFF).putChars(value);
		}

		var length = tmp.wIndex() - pos - 6;
		writeSynchSafeInt(tmp, pos, length);
	}
	void writeURITag(ByteList tmp, String id, URI value) {
		tmp.putAscii(id);
		var pos = tmp.wIndex();
		tmp.putInt(0).putShort(0).putAscii(value.toASCIIString());
		var length = tmp.wIndex() - pos - 6;
		writeSynchSafeInt(tmp, pos, length);
	}

	private static void writeSynchSafeInt(ByteList b, int off, int len) {b.put(off++, (len >>> 21) & 0x7F).put(off++, (len >>> 14) & 0x7F).put(off++, (len >>> 7) & 0x7F).put(off, (len) & 0x7F);}
}