package roj.config;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import roj.config.mapper.ObjectMapper;
import roj.config.mapper.ObjectMapperFactory;
import roj.config.node.ConfigValue;
import roj.config.node.xml.Element;
import roj.config.node.xml.Node;
import roj.config.table.CsvParser;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.ParseException;
import roj.text.TextWriter;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;
import roj.util.OperationDone;

import java.io.*;
import java.util.TimeZone;

/**
 * @author Roj234
 * @since 2024/3/23 21:00
 */
public enum ConfigMaster {
	BENCODE, NBT, XNBT, MSGPACK, JSON, YAML, XML, TOML, INI, CSV;

	public static ConfigMaster fromExtension(File path) {
		String ext = IOUtil.extensionName(path.getName());
		return switch (ext) {
			case "yml", "yaml" -> YAML;
			case "xml" -> XML;
			case "json", "json5" -> JSON;
			case "toml" -> TOML;
			case "ini" -> INI;
			case "nbt" -> NBT;
			case "torrent" -> BENCODE;
			case "csv" -> CSV;
			default -> throw new IllegalArgumentException("不支持的配置文件扩展名:"+ext);
		};
	}

	/**
	 * @see #parser(int)
	 */
	public BinaryParser parser() {return parser(0);}
	/**
	 * 返回的实例并非线程安全
	 * @param initFlag 初始化标记，无法在解析时动态修改，目前仅有{@link Parser#COMMENT}以支持注释
	 */
	private BinaryParser parser(@MagicConstant(flags = Parser.COMMENT) int initFlag) {
		return switch (this) {
			case JSON -> new JsonParser(initFlag);
			case YAML -> new YamlParser(initFlag);
			case XML -> new XmlParser();
			case TOML -> new TomlParser(initFlag);
			case INI -> new IniParser();
			case CSV -> new CsvParser();
			case NBT -> new NbtParser();
			case XNBT -> new NbtParserEx();
			case MSGPACK -> new MsgPackParser();
			case BENCODE -> new BEncodeParser();
		};
	}

	/**
	 * 创建一个到out的流式序列化器
	 * 不是所有格式都支持流式序列化
	 * @param out 可以是File、OutputStream或DynByteBuf
	 */
	private ValueEmitter serializer(Object out)  { return serializer(out, ""); }
	// 也不是所有格式都支持indent 哈哈
	private ValueEmitter serializer(Object out, String indent) {
		try {
			return switch (this) {
				case JSON -> new JsonSerializer(indent).to(textEncoder(out));
				case YAML -> new YamlSerializer(indent).multiline(true).timezone(TimeZone.getDefault()).to(textEncoder(out));
				case INI -> new IniSerializer().to(textEncoder(out));
				case BENCODE -> new BEncodeEncoder(binaryEncoder(out));
				case NBT, XNBT -> new NbtEncoder(binaryEncoder(out)).setXNbt(this == XNBT);
				case MSGPACK -> new MsgPackEncoder.Compressed(binaryEncoder(out));
				default -> throw new UnsupportedOperationException(this+"不支持流式序列化");
			};
		} catch (Exception e) {
			Helpers.athrow(e);
			throw OperationDone.NEVER;
		}
	}
	private boolean hasSerializer() { return ordinal() >= NBT.ordinal() && ordinal() <= YAML.ordinal(); }

	private static @NotNull DynByteBuf binaryEncoder(Object out) throws IOException {
		return out instanceof DynByteBuf buf
				? buf
				: new ByteList.ToStream(out instanceof File f
				? new FileOutputStream(f)
				: (OutputStream) out);
	}

	private static @NotNull CharList textEncoder(Object out) throws IOException {
		return out instanceof CharList sb
				? sb
				: out instanceof File f
				? TextWriter.to(f)
				: new TextWriter((Closeable) out, null);
	}

	/**
	 * 将entry序列化到out
	 * 不是所有格式都支持序列化
	 * @param out 可以是File、OutputStream或DynByteBuf
	 */
	private void serialize(Object out, ConfigValue entry) throws IOException {
		switch (this) {
			case JSON, YAML, INI, NBT, XNBT, MSGPACK, BENCODE -> {
				try (var emitter = serializer(out)) {
					entry.accept(emitter);
				}
			}
			case TOML, XML -> {
				try (TextWriter tw = out instanceof File f ? TextWriter.to(f) : new TextWriter((Closeable) out, null)) {
					if (this == TOML) {
						CharList tmp = new CharList();
						entry.appendTOML(tw, tmp);
						tmp._free();
					} else {
						((Element) entry).toXML(tw);
					}
				}
			}
			default -> throw new UnsupportedOperationException(this+"不支持序列化");
		}
	}

	public void toFile(ConfigValue entry, File file) throws IOException { serialize(file, entry); }
	public void toBytes(ConfigValue entry, OutputStream out) throws IOException { serialize(out, entry); }
	public void toBytes(ConfigValue entry, DynByteBuf buf) throws IOException { serialize(buf, entry); }

	public String toString(ConfigValue entry) { return toString(entry, IOUtil.getSharedCharBuf()).toString(); }
	public String toString(ConfigValue entry, String indent) { return toString(entry, IOUtil.getSharedCharBuf(), indent).toString(); }
	public CharList toString(ConfigValue entry, CharList out) { return toString(entry, out, ""); }
	public CharList toString(ConfigValue entry, CharList out, String indent) {
		switch (this) {
			case JSON, YAML, INI -> entry.accept(serializer(out, indent));
			case TOML -> entry.appendTOML(out, new CharList());
			case XML -> {
				Node node;
				if (entry instanceof Node n) {
					node = n;
				} else {
					// convert to xml
					XmlEmitter sb = new XmlEmitter();
					entry.accept(sb);
					node = sb.get();
				}

				if (indent.isEmpty()) node.toCompatXML(out);
				else node.toXML(out);
			}
			default -> throw new UnsupportedOperationException(this+"不支持序列化到字符串");
		}

		return out;
	}

	public ConfigValue parse(File key) throws IOException, ParseException { return parser().parse(key); }
	public ConfigValue parse(InputStream key) throws IOException, ParseException { return parser().parse(key); }
	public ConfigValue parse(DynByteBuf key) throws IOException, ParseException { return parser().parse(key); }
	public ConfigValue parse(CharSequence text) throws ParseException {
		BinaryParser p = parser();
		if (!(p instanceof Parser tp)) throw new UnsupportedOperationException(this+"不是文本配置格式");
		return tp.parse(text);
	}

	public <T> T readObject(Class<T> type, File file) throws IOException, ParseException { return readObject(ObjectMapperFactory.SAFE.serializer(type), file); }
	/**
	 * @implNote 由于部分二进制格式可能存在数据分界，所以不会关闭in
	 */
	public <T> T readObject(Class<T> type, InputStream in) throws IOException, ParseException { return readObject(ObjectMapperFactory.SAFE.serializer(type), in); }
	public <T> T readObject(Class<T> type, DynByteBuf buf) throws IOException, ParseException { return readObject(ObjectMapperFactory.SAFE.serializer(type), buf); }
	public <T> T readObject(Class<T> type, CharSequence sb) throws ParseException { return readObject(ObjectMapperFactory.SAFE.serializer(type), sb); }

	public <T> T readObject(ObjectMapper<T> ser, File file) throws IOException, ParseException {parser().parse(file, 0, ser.reset());return ser.get();}
	public <T> T readObject(ObjectMapper<T> ser, InputStream in) throws IOException, ParseException {parser().parse(in, 0, ser.reset());return ser.get();}
	public <T> T readObject(ObjectMapper<T> ser, DynByteBuf buf) throws IOException, ParseException {parser().parse(buf, 0, ser.reset());return ser.get();}
	public <T> T readObject(ObjectMapper<T> ser, CharSequence sb) throws ParseException {
		var p = parser();
		if (!(p instanceof Parser tp)) throw new UnsupportedOperationException(this+"不是文本配置格式");
		tp.parse(sb, 0, ser.reset());
		return ser.get();
	}

	@SuppressWarnings("unchecked")
	public void writeObject(Object o, File file) throws IOException { writeObject((ObjectMapper<Object>) ObjectMapperFactory.SAFE_SERIALIZE_ONLY.serializer(o.getClass()), o, file); }
	@SuppressWarnings("unchecked")
	public void writeObject(Object o, File file, String indent) throws IOException {writeObject((ObjectMapper<Object>) ObjectMapperFactory.SAFE_SERIALIZE_ONLY.serializer(o.getClass()), o, file, indent); }
	@SuppressWarnings("unchecked")
	public void writeObject(Object o, OutputStream out) throws IOException { writeObject((ObjectMapper<Object>) ObjectMapperFactory.SAFE_SERIALIZE_ONLY.serializer(o.getClass()), o, out); }
	@SuppressWarnings("unchecked")
	public DynByteBuf writeObject(Object o, DynByteBuf buf) throws IOException { return writeObject((ObjectMapper<Object>) ObjectMapperFactory.SAFE_SERIALIZE_ONLY.serializer(o.getClass()), o, buf); }
	@SuppressWarnings("unchecked")
	public CharList writeObject(Object o, CharList sb) { return writeObject((ObjectMapper<Object>) ObjectMapperFactory.SAFE_SERIALIZE_ONLY.serializer(o.getClass()), o, sb); }

	public <T> void writeObject(ObjectMapper<T> ser, T o, File file) throws IOException { writeAndClose(ser, o, file, ""); }
	public <T> void writeObject(ObjectMapper<T> ser, T o, File file, String indent) throws IOException { writeAndClose(ser, o, file, indent); }
	public <T> void writeObject(ObjectMapper<T> ser, T o, OutputStream out) throws IOException { writeAndClose(ser, o, out, ""); }
	public <T> void writeObject(ObjectMapper<T> ser, T o, OutputStream out, String indent) throws IOException { writeAndClose(ser, o, out, indent); }
	public <T> DynByteBuf writeObject(ObjectMapper<T> ser, T o, DynByteBuf buf) throws IOException { writeAndClose(ser, o, buf, ""); return buf; }
	public <T> DynByteBuf writeObject(ObjectMapper<T> ser, T o, DynByteBuf buf, String indent) throws IOException { writeAndClose(ser, o, buf, indent); return buf; }
	public <T> CharList writeObject(ObjectMapper<T> ser, T o, CharList sb) { ser.write(serializer(sb, ""), o); return sb; }
	public <T> CharList writeObject(ObjectMapper<T> ser, T o, CharList sb, String indent) { ser.write(serializer(sb, indent), o); return sb; }

	private <T> void writeAndClose(ObjectMapper<T> ser, T o, Object out, String indent) throws IOException {
		if (hasSerializer()) {
			try (ValueEmitter v = serializer(out, indent)) {
				ser.write(v, o);
			}
		} else {
			var tmp = new TreeEmitter();
			tmp.setProperty(TreeEmitter.ORDERED_MAP, true);
			ser.write(tmp, o);
			serialize(out, tmp.get());
		}
	}
}