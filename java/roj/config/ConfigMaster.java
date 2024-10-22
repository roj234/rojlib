package roj.config;

import roj.config.auto.Serializer;
import roj.config.auto.SerializerFactory;
import roj.config.data.CEntry;
import roj.config.serial.*;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextWriter;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.TimeZone;

/**
 * @author Roj234
 * @since 2024/3/23 0023 21:00
 */
public enum ConfigMaster {
	BENCODE, NBT, XNBT, JSON, YAML, XML, TOML, INI, CSV;

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

	// region 序列化
	/**
	 * 将entry序列化到out
	 * 不是所有格式都支持序列化
	 * @param out 可以是File、OutputStream或DynByteBuf
	 */
	public void serialize(Object out, CEntry entry) throws IOException {
		switch (this) {
			case JSON, YAML, NBT, XNBT -> {
				try (CVisitor v = serializer(out)) {
					entry.accept(v);
				}
			}
			case TOML,INI,XML -> {
				try (TextWriter tw = out instanceof File f ? TextWriter.to(f) : new TextWriter((Closeable) out, StandardCharsets.UTF_8)) {
					if (this == TOML) {
						CharList tmp = new CharList();
						entry.appendTOML(tw, tmp);
						tmp._free();
					} else if (this == INI) {
						entry.appendINI(tw);
					} else {
						CVisitor sb = new ToXml();
						entry.accept(sb);
					}
				}
			}
			case BENCODE -> {
				try (DynByteBuf ob = out instanceof DynByteBuf buf
					? buf
					: new ByteList.ToStream(out instanceof File f
					? new FileOutputStream(f)
					: (OutputStream) out)) {

					entry.toB_encode(ob);
				}
			}
			default -> throw new UnsupportedOperationException(this+"不支持序列化");
		}
	}
	public boolean canSerialize() { return ordinal() <= INI.ordinal(); }

	/**
	 * 创建一个到out的流式序列化器
	 * 不是所有格式都支持流式序列化
	 * @param out 可以是File、OutputStream或DynByteBuf
	 */
	public CVisitor serializer(Object out) throws IOException { return serializer(out, ""); }
	// 也不是所有格式都支持indent 哈哈
	public CVisitor serializer(Object out, String indent) throws IOException {
		switch (this) {
			case JSON, YAML -> {
				TextWriter tw = out instanceof File f ? TextWriter.to(f) : new TextWriter((Closeable) out, StandardCharsets.UTF_8);
				return (this == JSON ? new ToJson(indent) : new ToYaml(indent)).sb(tw);
			}
			case NBT, XNBT -> {
				DynByteBuf ob = out instanceof DynByteBuf buf
					? buf
					: new ByteList.ToStream(out instanceof File f
					? new FileOutputStream(f)
					: (OutputStream) out);
				return new ToNBT(ob).setXNbt(this == XNBT);
			}
			default -> throw new UnsupportedOperationException(this+"不支持流式序列化");
		}
	}
	public boolean hasSerializer() { return ordinal() >= NBT.ordinal() && ordinal() <= YAML.ordinal(); }

	public void toFile(CEntry entry, File file) throws IOException { serialize(file, entry); }
	public void toBytes(CEntry entry, OutputStream out) throws IOException { serialize(out, entry); }
	public void toBytes(CEntry entry, DynByteBuf buf) throws IOException { serialize(buf, entry); }
	// endregion
	// region 序列化到字符串
	public CVisitor textSerializer(CharList out, String indent) {
		return switch (this) {
			case JSON -> new ToJson(indent).sb(out);
			case YAML -> new ToYaml(indent).multiline(true).timezone(TimeZone.getDefault()).sb(out);
			default -> throw new UnsupportedOperationException(this+"不支持流式序列化到字符串");
		};
	}

	public String toString(CEntry entry) { return toString(entry, IOUtil.getSharedCharBuf()).toString(); }
	public String toString(CEntry entry, String indent) { return toString(entry, IOUtil.getSharedCharBuf(), indent).toString(); }
	public CharList toString(CEntry entry, CharList out) { return toString(entry, out, ""); }
	public CharList toString(CEntry entry, CharList out, String indent) {
		switch (this) {
			case JSON, YAML -> entry.accept(textSerializer(out, indent));
			case TOML -> entry.appendTOML(out, new CharList());
			case INI -> entry.appendINI(out);
			case XML -> {
				// convert to xml
				ToXml sb = new ToXml();
				entry.accept(sb);
				sb.get().toCompatXML(out);
			}
			default -> throw new UnsupportedOperationException(this+"不支持序列化到字符串");
		}

		return out;
	}
	// endregion

	public CEntry parse(File key) throws IOException, ParseException { return parser(false).parse(key); }
	public CEntry parse(InputStream key) throws IOException, ParseException { return parser(false).parse(key); }
	public CEntry parse(DynByteBuf key) throws IOException, ParseException { return parser(false).parse(key); }
	public CEntry parse(CharSequence sb) throws ParseException {
		BinaryParser p = parser(false);
		if (!(p instanceof Parser tp)) throw new UnsupportedOperationException(this+"不是文本配置格式");
		return tp.parse(sb);
	}

	public BinaryParser parser(boolean visitorMode) {
		return switch (this) {
			case JSON -> visitorMode ? new CCJson() : new JSONParser();
			case YAML -> visitorMode ? new CCYaml() : new YAMLParser();
			case XML -> new XMLParser();
			case TOML -> new TOMLParser();
			case INI -> new IniParser();
			case CSV -> new CsvParser();
			case NBT -> new NBTParser();
			case XNBT -> new XNBTParser();
			case BENCODE -> new BencodeParser();
		};
	}

	public <T> T readObject(Class<T> type, File file) throws IOException, ParseException { return readObject(SerializerFactory.SAFE.serializer(type), file); }
	/**
	 * @implNote 由于部分二进制格式可能存在数据分界，所以不会关闭in
	 */
	public <T> T readObject(Class<T> type, InputStream in) throws IOException, ParseException { return readObject(SerializerFactory.SAFE.serializer(type), in); }
	public <T> T readObject(Class<T> type, DynByteBuf buf) throws IOException, ParseException { return readObject(SerializerFactory.SAFE.serializer(type), buf); }
	public <T> T readObject(Class<T> type, CharSequence sb) throws ParseException { return readObject(SerializerFactory.SAFE.serializer(type), sb); }

	public <T> T readObject(Serializer<T> ser, File file) throws IOException, ParseException {parser(true).parse(file, 0, ser.reset());return ser.get();}
	public <T> T readObject(Serializer<T> ser, InputStream in) throws IOException, ParseException {parser(true).parse(in, 0, ser.reset());return ser.get();}
	public <T> T readObject(Serializer<T> ser, DynByteBuf buf) throws IOException, ParseException {parser(true).parse(buf, 0, ser.reset());return ser.get();}
	public <T> T readObject(Serializer<T> ser, CharSequence sb) throws ParseException {
		var p = parser(true);
		if (!(p instanceof Parser tp)) throw new UnsupportedOperationException(this+"不是文本配置格式");
		tp.parse(sb, 0, ser.reset());
		return ser.get();
	}

	@SuppressWarnings("unchecked")
	public void writeObject(Object o, File file) throws IOException { writeObject((Serializer<Object>) SerializerFactory.SAFE.serializer(o.getClass()), o, file); }
	@SuppressWarnings("unchecked")
	public void writeObject(Object o, OutputStream out) throws IOException { writeObject((Serializer<Object>) SerializerFactory.SAFE.serializer(o.getClass()), o, out); }
	@SuppressWarnings("unchecked")
	public DynByteBuf writeObject(Object o, DynByteBuf buf) throws IOException { return writeObject((Serializer<Object>) SerializerFactory.SAFE.serializer(o.getClass()), o, buf); }
	@SuppressWarnings("unchecked")
	public CharList writeObject(Object o, CharList sb) { return writeObject((Serializer<Object>) SerializerFactory.SAFE.serializer(o.getClass()), o, sb); }

	public <T> void writeObject(Serializer<T> ser, T o, File file) throws IOException { writeObject0(ser, o, file, ""); }
	public <T> void writeObject(Serializer<T> ser, T o, File file, String indent) throws IOException { writeObject0(ser, o, file, indent); }
	public <T> void writeObject(Serializer<T> ser, T o, OutputStream out) throws IOException { writeObject0(ser, o, out, ""); }
	public <T> DynByteBuf writeObject(Serializer<T> ser, T o, DynByteBuf buf) throws IOException { writeObject0(ser, o, buf, ""); return buf; }
	public <T> CharList writeObject(Serializer<T> ser, T o, CharList sb) { ser.write(textSerializer(sb, ""), o); return sb; }

	private <T> void writeObject0(Serializer<T> ser, T o, Object out, String indent) throws IOException {
		if (hasSerializer()) {
			try (CVisitor v = serializer(out, indent)) {
				ser.write(v, o);
			}
		} else {
			var tmp = new ToEntry();
			tmp.setProperty("centry:linked_map", true);
			ser.write(tmp, o);
			serialize(out, tmp.get());
		}
	}
}