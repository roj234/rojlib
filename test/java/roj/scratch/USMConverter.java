package roj.scratch;

import org.jetbrains.annotations.NotNull;
import roj.config.node.ByteArrayValue;
import roj.config.node.ConfigValue;
import roj.config.node.ListValue;
import roj.config.node.MapValue;
import roj.io.XDataInputStream;
import roj.io.source.FileSource;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * @author Roj234
 * @since 2025/09/04 19:52
 */
public class USMConverter {
	static final int PT_STREAM = 0, PT_HEADER = 1, PT_END = 2, PT_SEEK = 3;
	static OutputStream videoOut, audioOut;

	public static void main(String[] args) throws Exception {
		var file = new FileSource(args[0]);
		var in = XDataInputStream.wrap(file.asInputStream());

		var type = in.readAscii(4);
		assert type.equals("CRID");
		var dataLen = in.readInt();
		var body = DynByteBuf.wrap(in.readBytes(dataLen));
		int rsv0 = body.readUnsignedByte();
		int off = body.readUnsignedByte();
		assert off == 24;
		int pad = body.readUnsignedShort();
		int channel = body.readUnsignedByte();
		int rsv1 = body.readUnsignedShort();
		int dataType = body.readUnsignedByte();
		int frameTime = body.readInt();
		int framerate = body.readInt();
		long rsv2 = body.readLong();
		body.wIndex(body.wIndex() - pad);
		System.out.println("len="+dataLen+",pad="+pad+",ch="+channel+",pt="+dataType+",frameTime="+frameTime+",framerate="+(framerate/100f));
		if (body.readAscii(4).equals("@UTF")) {
			decodeUTFTable(body);
		} else {
			body.rIndex -= 4;
			System.out.println(body.dump());
		}

		videoOut = new FileOutputStream("video.ivf");
		audioOut = new FileOutputStream("audio.adx");

		while (in.isReadable()) {
			type = in.readAscii(4);
			dataLen = in.readInt();
			body = DynByteBuf.wrap(in.readBytes(dataLen));

			rsv0 = body.readUnsignedByte();
			off = body.readUnsignedByte();
			assert off == 24;
			pad = body.readUnsignedShort();
			channel = body.readUnsignedByte();
			rsv1 = body.readUnsignedShort();
			dataType = body.readUnsignedByte();
			frameTime = body.readInt();
			framerate = body.readInt();
			rsv2 = body.readLong();
			body.wIndex(body.wIndex() - pad);
			if (dataType != PT_STREAM) {
				if (body.readAscii(4).equals("@UTF")) {
					decodeUTFTable(body);
				} else {
					body.rIndex -= 4;
					System.out.println(body.dump());
				}
				continue;
			}

			if ("@SFV".equals(type)) { // 视频帧
				System.out.println("Video stream#"+channel+" "+(dataLen-pad-off)+"bytes "+(framerate/100f)+"FPS,dataType="+dataType+",frameTime="+frameTime);
				// 剥离payload中的padding并获取帧
				body.writeToStream(videoOut);
			} else if ("@SFA".equals(type)) { // 音频帧
				System.out.println("Audio stream#"+channel+" "+(dataLen-pad-off)+"bytes "+(framerate/100f)+"FPS,dataType="+dataType+",frameTime="+frameTime);
				// 记录音频数据加上时间戳
				body.writeToStream(audioOut);
			}
		}
	}

	private static void decodeUTFTable(ByteList body) {
		int len = body.readInt();
		var mem = body.slice();

		int dataPtr = body.readInt();
		int strOffset = body.readInt();
		int byteOffset = body.readInt();

		String name = readString(mem, strOffset, body.readInt());

		int columns = body.readUnsignedShort();
		int dataSize = body.readUnsignedShort();
		int rows = body.readInt();

		var uniqueArray = mem.slice();
		uniqueArray.rIndex += dataPtr;

		var init = body.rIndex;

		var list = new ListValue();
		for (int i = 0; i < rows; i++) {
			var map = new MapValue();
			for (int j = 0; j < columns; j++) {
				int marker = body.readUnsignedByte();
				String key = readString(mem, strOffset, body.readInt());

				DynByteBuf src = (marker & 64) != 0 ? uniqueArray : body;

				ConfigValue value = switch (marker & 0xF) {
					case 0 -> ConfigValue.valueOf(src.readByte());
					case 1 -> ConfigValue.valueOf(src.readUnsignedByte());
					case 2 -> ConfigValue.valueOf(src.readShort());
					case 3 -> ConfigValue.valueOf(src.readUnsignedShort());
					case 4 -> ConfigValue.valueOf(src.readInt());
					case 5 -> ConfigValue.valueOf(src.readUnsignedInt());
					case 6 -> ConfigValue.valueOf(src.readLong());
					case 7 -> ConfigValue.valueOf(src.readLong());
					case 8 -> ConfigValue.valueOf(src.readFloat());
					case 9 -> ConfigValue.valueOf(src.readDouble());
					case 10 -> ConfigValue.valueOf(readString(mem, strOffset, src.readInt()));
					case 11 -> {
						int start = src.readInt();
						int end = src.readInt();
						yield new ByteArrayValue(uniqueArray.slice(start, end - start).toByteArray());
					}
					default -> throw new UnsupportedOperationException(body.dump());
				};

				map.put(key, value);
			}
			body.rIndex = init;

			list.add(map);
		}

		System.out.println("table name="+name);
		System.out.println(list);
	}

	@NotNull
	private static String readString(DynByteBuf mem, int strOffset, int keyOff) {
		DynByteBuf stringTab = mem.slice();
		stringTab.rIndex = strOffset + keyOff;
		int len = stringTab.readCString(stringTab.readableBytes());
		return stringTab.readGB(len);
	}
}
