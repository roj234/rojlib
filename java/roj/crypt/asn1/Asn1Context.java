package roj.crypt.asn1;

import roj.collect.*;
import roj.config.ParseException;
import roj.config.Word;
import roj.config.data.*;
import roj.io.CorruptedInputException;
import roj.io.IOUtil;
import roj.text.CharList;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Map;

import static roj.config.Word.INTEGER;
import static roj.config.Word.LITERAL;
import static roj.crypt.asn1.Asn1Tokenizer.*;

/**
 * Reference:
 * <a href="https://letsencrypt.org/zh-cn/docs/a-warm-welcome-to-asn1-and-der/">ASN.1 与 DER 轻松入门</a>
 * <a href="https://lapo.it/asn1js/">ASN.1 JavaScript decoder</a>
 * <a href="https://datatracker.ietf.org/doc/html/rfc2315">RFC 2315 - PKCS #7</a>
 * <a href="https://oidref.com/">Global OID reference database</a>
 * <a href="https://trustee.ietf.org/about/faq/#reproducing-rfcs:~:text=It%20is%20common,statement%20if%20present">Am I allowed to reproduce extracts from RFCs?</a>
 *
 * @author Roj234
 * @since 2024/3/22 22:58
 */
public class Asn1Context {
	protected final Map<String, Type> map = new MyHashMap<>();
	private final MyHashSet<CEntry> byVal = new MyHashSet<>();

	@Override
	public String toString() {return "Asn1Context{" + "map=" + map + ", namedOid=" + byVal + '}';}

	static final ThreadLocal<Asn1Context> CURRENT_PARSING = new ThreadLocal<>();
	public CEntry parse(String struct, DerReader in) throws IOException {
		Type type = map.get(struct);

		CURRENT_PARSING.set(this);
		try {
			return type.parse(in.readType(), in);
		} finally {
			CURRENT_PARSING.remove();
		}
	}
	public void write(String struct, CEntry value, DerWriter out) throws IOException {
		Type type = map.get(struct);

		out.begin(type.derType());
		type.write(value, out);
		out.end();
	}

	protected Type unmarshalAny(String struct, String target, CEntry value) {
		if (struct.equals("ContentInfo")) {
			Object oid = byVal.find1(value);
			if (oid == IntMap.UNDEFINED) throw new UnsupportedOperationException("未知的OID "+value);
			return map.get(((NamedOID)oid).name);
		}

		if (struct.equals("AlgorithmIdentifier")) {
			Object oid = byVal.find1(value);
			// "2.16.840.1.101.3.4.2.1" sha256
			// "1.2.840.113549.1.1.5" sha1WithRSAEncryption
			// "1.2.840.113549.1.1.1" rsaEncryption
			return Simple.NULL;
		}

		if (struct.equals("AttributeTypeAndValue")) {
			// "2.5.4.6" countryName
			// "2.5.4.10" organizationName
			// "2.5.4.11" organizationalUnitName
			int[] _oid = ((CIntArray) value).value;
			if (_oid[0] == 2 && _oid[1] == 5 && _oid[2] == 4)
				return Simple.PrintableString;
		}

		throw new IllegalStateException("无法解析ANY类型\n数据: "+value+"\n结构: "+struct+'.'+target);
	}

	static final class NamedOID extends CIntArray {
		final String name;
		NamedOID(String name, int... oid) {
			super(oid);
			this.name = name;
		}
		@Override
		public String toString() { return "OID["+name+"] = ["+Arrays.toString(value)+"]"; }
	}

	private static final byte UNSPECIFIED = 0b00111111, OPTIONAL = (byte) 128, EXPLICIT = 64;

	public sealed interface Type permits Simple, BaseType {
		int derType();
		CharList append(CharList sb, int prefix);
		CEntry parse(int type, DerReader in) throws IOException;
		void write(CEntry val, DerWriter out) throws IOException;
		default void setName(String name) {}
		default CEntry translate(String name) { throw new UnsupportedOperationException(getClass().getName()+"无法解析"+name); }
	}
	private sealed abstract static class BaseType implements Type permits Alias, Choice, Enum, List, Range, Struct {
		@Override
		public final String toString() { return append(IOUtil.getSharedCharBuf(), 0).toString(); }
	}
	protected enum Simple implements Type {
		INTEGER(DerValue.INTEGER), BIT_STRING(DerValue.BIT_STRING), OCTET_STRING(DerValue.OCTET_STRING), NULL(DerValue.NULL),
		UTF8_STRING(DerValue.UTF8_STRING), PrintableString(DerValue.PrintableString), IA5_STRING(DerValue.IA5String),
		UTCTime(DerValue.UTCTime), GeneralizedTime(DerValue.GeneralizedTime),
		OID(DerValue.OID), ANY, BOOLEAN;
		static final Simple[] VALUES = values();

		final byte derType;
		Simple() { derType = -1; }
		Simple(int derType) { this.derType = (byte) derType; }

		public int derType() { return derType; }
		public CharList append(CharList sb, int prefix) { return sb.append(name()); }
		public CEntry parse(int type, DerReader in) throws IOException {
			int len = in.readLength();
			return switch (this) {
				case INTEGER -> in.readInt(len);
				case BIT_STRING -> in.readBits(len);
				case OCTET_STRING -> in.readBytes(len);
				case NULL -> CNull.NULL;
				case UTF8_STRING -> in.readUTF(len);
				case PrintableString, IA5_STRING, UTCTime, GeneralizedTime -> in.readIso(len);
				case OID -> in.readOid(len);
				case ANY -> in.readOpaque(type, len);
				case BOOLEAN -> in.readInt(len); // should be 1
			};
		}
		public void write(CEntry val, DerWriter out) throws IOException {
			switch (this) {
				case INTEGER -> out.writeInt(((DerValue.Int)val).value);
				case BIT_STRING -> {
					var bits = (DerValue.Bits) val;
					out.writeBits(bits.value, bits.bits);
				}
				case OCTET_STRING -> out.writeBytes(((CByteArray)val).value);
				case UTF8_STRING -> out.writeUTF(val.asString());
				case PrintableString, IA5_STRING, UTCTime, GeneralizedTime -> out.writeIso(derType, val.asString());
				case OID -> out.writeOid(((CIntArray) val).value);
				case ANY -> {
					var opq = (DerValue.Opaque) val;
					out.write(opq.type, opq.value);
				}
				case BOOLEAN -> out.writeInt(val.asBool()?BigInteger.ONE:BigInteger.ZERO);
			};
		}
		public CEntry translate(String name) {
			return switch (this) {
				case INTEGER -> DerValue.INTEGER(new BigInteger(name));
				default -> Type.super.translate(name);
			};
		}
	}
	/**
	 * X { name(value) }
 	 */
	private static final class Enum extends BaseType {
		private final byte original;
		private final String[] names;
		private final CEntry[] values;

		public Enum(Type type, SimpleList<MapStub> stubs) {
			original = (byte) ((Simple) type).ordinal();
			int len = stubs.size();
			names = new String[len];
			values = new CEntry[len];
			for (int i = 0; i < len; i++) {
				MapStub stub = stubs.get(i);
				names[i] = stub.name;
				values[i] = type.translate(stub.defVal.toString());
			}
		}

		public int derType() { return Simple.VALUES[original].derType; }
		public CharList append(CharList sb, int prefix) {
			sb.append(Simple.VALUES[original]).append(" { ");
			int i = 0;
			while (true) {
				sb.append(names[i]).append('(').append(values[i]).append(')');
				if (++i == names.length) return sb.append(" }");
				sb.append(',');
			}
		}
		@Override
		public CEntry parse(int type, DerReader in) throws IOException {
			CEntry value = Simple.VALUES[original].parse(type, in);
			for (CEntry v1 : values) {
				if (value.equals(v1)) return value;
			}
			throw new CorruptedInputException("value("+value+") not in enum "+this);
		}
		@Override
		public void write(CEntry val, DerWriter out) throws IOException {Simple.VALUES[original].write(val, out);}

		@Override
		public CEntry translate(String name) {
			for (int i = 0; i < values.length; i++) {
				if (names[i].equals(name))
					return values[i];
			}
			return super.translate(name);
		}
	}
	/**
	 * CHOICE
	 */
	private static final class Choice extends BaseType {
		private final String[] names;
		private final Type[] choices;
		private final byte[] rules;
		private final CharMap<Type> mappedTarget;

		public Choice(SimpleList<MapStub> stubs) {
			int len = stubs.size();
			names = new String[len];
			choices = new Type[len];
			rules = new byte[len];
			mappedTarget = new CharMap<>();
			for (int i = 0; i < len; i++) {
				MapStub stub = stubs.get(i);
				Type type = stub.type;
				byte flag = stub.flag;

				if (stub.definedBy != null || stub.defVal != null || (flag&OPTIONAL) != 0)
					throw new IllegalStateException("不支持的选项 "+stub);

				names[i] = stub.name;
				choices[i] = type;
				rules[i] = flag;

				int key;
				if (flag == UNSPECIFIED) {
					key = type.derType();
					if (key == -1) throw new IllegalStateException("无法获知的类型 "+type);
				} else {
					key = flag&63;
					if ((flag&EXPLICIT) != 0) key |= 0xA0;
					else key |= 0x80;
				}

				Type prev = mappedTarget.put((char) key, type);
				if (prev != null) throw new IllegalStateException("冲突的选项 "+stubs);
			}
		}

		public int derType() { return -1; }
		public CharList append(CharList sb, int prefix) {
			sb.append("CHOICE {\n");
			int i = 0;
			while (true) {
				sb.padEnd('\t', prefix+1).append(names[i]).append(' ');
				int flag = rules[i];
				if (flag != UNSPECIFIED && flag != OPTIONAL) {
					sb.append('[').append(flag&31).append("] ")
					  .append((flag & EXPLICIT) == 0 ? "IMPLICIT " : "EXPLICIT ");
				}
				choices[i].append(sb, prefix+1);
				if (++i == choices.length) return sb.append('\n').padEnd('\t', prefix).append('}');
				sb.append(", \n");
			}
		}
		public CEntry parse(int type, DerReader in) throws IOException {
			int typeRef = type;

			Type type1 = mappedTarget.get((char)type);
			if (type1 == null) throw new CorruptedInputException("选项("+type+")不在范围中:"+this);
			if ((type&EXPLICIT) != 0) {
				in.readLength();
				type = in.readType();
			}

			CEntry ref = type1.parse(type, in);
			return DerValue.CHOICE(typeRef, ref);
		}
		public void write(CEntry val, DerWriter out) throws IOException {
			byte type = ((DerValue.Choice) val).encType;
			Type type1 = mappedTarget.get((char) type);
			if (type1 == null) throw new CorruptedInputException("选项("+type+")不在范围中:"+this);
			type1.write(val, out);
		}
	}
	/**
	 * A ::= B
	 */
	private static final class Alias extends BaseType {
		private final String alias;
		private final Type original;
		Alias(String alias, Type original) { this.alias = alias; this.original = original; }
		public int derType() { return original.derType(); }
		public CharList append(CharList sb, int prefix) {return sb.append(alias).append(" (ref ").append(original.getClass().getSimpleName()).append(")");}
		public CEntry parse(int type, DerReader in) throws IOException {return original.parse(type, in);}
		public void write(CEntry val, DerWriter out) throws IOException {original.write(val, out);}
		public CEntry translate(String name) { return original.translate(name); }
	}
	/**
	 * SIZE (1..MAX)
	 */
	private static final class Range extends BaseType {
		private final Type original;
		private final int min, max;
		Range(Type original, int min, int max) { this.original = original; this.min = min; this.max = max; }
		public int derType() { return original.derType(); }
		public CharList append(CharList sb, int prefix) {return original.append(sb.append("SIZE (").append(min).append("...").append(max).append(") "), prefix);}
		public CEntry parse(int type, DerReader in) throws IOException {
			CEntry value = original.parse(type, in);
			checkRange(value);
			return value;
		}
		public void write(CEntry val, DerWriter out) throws IOException {checkRange(val);original.write(val, out);}
		private void checkRange(CEntry value) throws CorruptedInputException {
			if (value instanceof CList c) {
				java.util.List<CEntry> raw = c.raw();
				if (raw.size() < min || raw.size() > max) throw new CorruptedInputException("值("+value+") 超出范围: "+this);
			} else if (value instanceof CString s) {
				if (s.value.length() < min || s.value.length() > max) throw new CorruptedInputException("值("+value+") 超出范围: "+this);
			} else {
				throw new CorruptedInputException("值("+value+") 无法定义范围: "+this);
			}
		}
	}
	/**
	 * SET OF
	 */
	private static final class List extends BaseType {
		private final boolean set;
		private final Type type;
		List(boolean set, Type type) { this.set = set; this.type = type; }
		public int derType() { return set?DerValue.SET:DerValue.SEQUENCE; }
		public CharList append(CharList sb, int prefix) {return type.append(sb.append(set?"SET":"SEQUENCE").append(" OF["), prefix).append(']');}
		public CEntry parse(int type, DerReader in) throws IOException {
			int end = in.readLength() + in.position();
			SimpleList<CEntry> values = new SimpleList<>();
			while (in.position() < end) values.add(this.type.parse(in.readType(), in));

			if (in.position() != end) throw new CorruptedInputException("length mismatch");
			return new CList(values);
		}
		public void write(CEntry val, DerWriter out) throws IOException {
			for (CEntry entry : val.asList().raw()) type.write(entry, out);
			if (set) out.sort();
		}
	}
	/**
	 * SEQUENCE
	 */
	private static final class Struct extends BaseType {
		private String name;
		private final String[] names;
		private final IntBiMap<String> index;
		private final Type[] types;
		private final byte[] flags;
		private final Object[] context;
		private final boolean set;

		public Struct(boolean set, SimpleList<MapStub> stubs) {
			this.set = set;
			int len = stubs.size();
			index = new IntBiMap<>(len);
			names = new String[len];
			types = new Type[len];
			flags = new byte[len];
			context = new Object[len];
			for (int i = 0; i < len; i++) {
				MapStub stub = stubs.get(i);
				names[i] = stub.name;
				types[i] = stub.type;
				flags[i] = stub.flag;
				index.putInt(i, stub.name);
			}

			java.util.List<String> list = Arrays.asList(names);
			for (int i = 0; i < len; i++) {
				MapStub stub = stubs.get(i);
				if (stub.definedBy != null) {
					assert stub.type == Simple.ANY;
					int pos = list.indexOf(stub.definedBy);
					if (pos >= i) throw new IllegalStateException("非法后向引用");
					context[i] = pos;
				} else {
					context[i] = stub.defVal;
				}
			}
		}
		public int derType() { return set?DerValue.SET:DerValue.SEQUENCE; }
		public CharList append(CharList sb, int prefix) {
			sb.append(set ? "SET { (" : "SEQUENCE { (").append(name).append(")\n");
			int i = 0;
			while (true) {
				sb.padEnd('\t', prefix+1).append(names[i]).append(' ');
				int flag = flags[i];
				if (flag != UNSPECIFIED && flag != OPTIONAL) {
					sb.append('[').append(flag&31).append("] ")
					  .append((flag & EXPLICIT) == 0 ? "IMPLICIT " : "EXPLICIT ");
				}
				types[i].append(sb, prefix+1);
				if ((flag&OPTIONAL) != 0) sb.append(" OPTIONAL");
				if (context[i] != null) {
					sb.append(types[i] == Simple.ANY ? " DEFINED BY " : " DEFAULT ").append(context[i]);
				}

				if (++i == names.length) return sb.append('\n').padEnd('\t', prefix).append('}');
				sb.append(", \n");
			}
		}
		public CEntry parse(int type, DerReader in) throws IOException {
			if (names.length == 0) return Simple.ANY.parse(type, in);

			int end = in.readLength()+in.position();
			SimpleList<CEntry> values = new SimpleList<>(names.length);
			int i = 0;
			if (in.position() < end) {
				type = in.readType();

				while (true) {
					String name = names[i];
					Type target = types[i];
					int flag = flags[i];
					Object ctx = context[i];

					i++;

					boolean mustParse;
					if (target == Simple.ANY && ctx != null) {
						target = CURRENT_PARSING.get().unmarshalAny(this.name, name, values.get((int)ctx));
						assert target != Simple.ANY;
						block:
						if (target == null) {
							if ((flag&OPTIONAL) == 0) {
								target = Simple.ANY;
								break block;
								//throw new CorruptedInputException("属性"+this.name+"."+name+"是必须的,不能是null: ");
							}
							if (i == names.length) {
								target = Simple.ANY;
								break block;
								//throw new CorruptedInputException("属性"+this.name+"."+name+"不能省略，因为结构还有剩余数据");
							}

							values.add(null);
							continue;
						}

						mustParse = true;
					} else {
						mustParse = flag == UNSPECIFIED;
					}

					if (mustParse || ((flag ^ type) & 31) == 0) {
						if ((flag&EXPLICIT) != 0) {
							if (type != (0xA0 | (flag&31))) throw new CorruptedInputException("incorrect EXPLICIT tag "+name+" in "+this);
							in.readLength();
							type = in.readType();
						}

						values.add(target.parse(type, in));
					} else {
						values.add(ctx == null ? null :
									ctx instanceof CEntry d ? d :
									(CEntry) (context[i-1] = target.translate(ctx.toString())));
						continue;
					}

					if (in.position() >= end) break;

					type = in.readType();
				}
			}

			if (in.position() != end) throw new CorruptedInputException("length mismatch, pos="+in.position()+", except="+end);
			return new DerValue.Sequence(name, index, values);
		}
		public void write(CEntry val, DerWriter out) throws IOException {
			Map<String, CEntry> map = val.asMap().raw();
			for (int i = 0; i < names.length; i++) {
				String name = names[i];
				Type target = types[i];
				int flag = flags[i];
				Object def = context[i];

				CEntry entry = map.get(name);
				if (entry != null && entry != def) {
					int type;
					if (flag != 0) {
						type = flag&31;
						if ((flag&EXPLICIT) != 0) {
							out.begin(type|0xA0);
							type = target.derType();
						} else {
							type |= 0x80;
						}
					} else {
						type = target.derType();
					}

					out.begin(type);

					target.write(entry, out);

					if ((flag&EXPLICIT) != 0) out.end();
					out.end();

				} else {
					if ((flag&OPTIONAL) == 0) throw new CorruptedInputException("结构"+this.name+"缺少必选参数["+i+"]"+name);
				}
			}
		}
		public void setName(String name) { this.name = name; }
	}

	private static final class MapStub {
		String name;
		byte flag;
		Type type;
		String definedBy;
		Object defVal;

		@Override
		public String toString() {
			return "MapStub{" + "name='" + name + '\'' + ", flag=" + flag + ", type=" + type + ", definedBy='" + definedBy + '\'' + ", defVal=" + defVal + '}';
		}
	}

	public static Asn1Context createFromString(String s) throws ParseException {
		Asn1Context ctx = new Asn1Context();

		Asn1Tokenizer wr = new Asn1Tokenizer();
		wr.init(s);

		IntList oids = new IntList();
		SimpleList<MapStub> stubs = new SimpleList<>();
		MyHashMap<String, NamedOID> OidByName = new MyHashMap<>();

		while (wr.hasNext()) {
			String typeName = wr.except(LITERAL, "ObjectName").val();

			Word w = wr.next();
			if (w.type() == AsnOid) {
				wr.except(is, "::=");
				wr.except(lBrace, "{");

				oids.clear();
				loop:
				while (true) {
					w = wr.next();
					switch (w.type()) {
						default -> throw wr.err("未预料的字符: "+w);
						case rBrace -> {break loop;}
						case INTEGER -> oids.add(w.asInt());
						case LITERAL -> {
							String objName = w.val();

							w = wr.next();
							if (w.type() == lParen) {
								oids.add(wr.except(INTEGER, "OidPart").asInt());
								wr.except(rParen, ")");
							} else {
								wr.retractWord();

								NamedOID ref = OidByName.get(objName);
								if (ref == null) throw wr.err("前向引用未注册的OID: " + objName);
								if (oids.size() != 0) throw wr.err("前缀OID只能是第一个: " + oids);
								oids.addAll(ref.value);
							}
						}
					}
				}

				NamedOID myOid = new NamedOID(typeName, oids.toArray());
				ctx.byVal.add(myOid);
				OidByName.put(typeName, myOid);
				continue;
			} else if (w.type() != is) throw wr.err("未预料的字符: "+w);

			Type type = readType(wr.next(), wr, stubs, ctx);
			type.setName(typeName);
			ctx.map.put(typeName, type);
		}

		return ctx;
	}

	private static Type readType(Word w, Asn1Tokenizer wr, SimpleList<MapStub> stubs, Asn1Context ctx) throws ParseException {
		var strict = false;

		Type type;
		if (w.type() >= 10 && w.type() <= AsnBoolean) {
			type = Simple.VALUES[w.type()-10];

			w = wr.next();
			if (w.val().equals("SIZE")) {
				// 符串、OCTET STRING、BIT STRING
				long minMax = readSize(wr);
				type = new Range(type, (int) (minMax>>>32), (int) minMax);
			} else if (w.type() == lBrace) {
				// 类型定义结尾可以使用花括号限定该类型只能取某些值。 例如此处定义 Version 字段只能取三个值中的一个，并对这三个值分别赋予了含义：
				// Version ::= INTEGER { v1(0), v2(1), v3(2) }
				while (true) {
					w = wr.next();
					if (w.type() == rBrace) break;
					if (w.type() != LITERAL) throw wr.err("期待EnumName");
					String name = w.val();
					wr.except(lParen, "(");
					int value = wr.except(INTEGER, "EnumValue").asInt();
					wr.except(rParen, ")");

					MapStub stub = new MapStub();
					stub.name = name;
					stub.defVal = value;

					w = wr.next();
					if (w.type() != comma) wr.retractWord();

					stubs.add(stub);
				}

				type = new Enum(type, stubs);
				stubs.clear();
			} else {
				wr.retractWord();
			}
		} else switch (w.type()) {
			default -> throw wr.err("未预料的字符: "+w);
			case LITERAL -> {
				Type original = ctx.map.get(w.val());
				if (strict && original == null) throw wr.err("找不到该结构: "+w.val());
				type = new Alias(w.val(), original);
			}
			case AsnSetOf, AsnSequenceOf -> type = new List(w.type() == AsnSetOf, readType(wr.next(), wr, new SimpleList<>(), ctx));
			case AsnSet, AsnSequence -> {
				boolean unordered = w.type() == AsnSet;

				w = wr.next();
				if (w.type() != lBrace) {
					if (w.val().equals("SIZE")) {
						long minMax = readSize(wr);
						type = new List(unordered, readType(wr.next(), wr, new SimpleList<>(), ctx));
						type = new Range(type, (int) (minMax>>>32), (int) minMax);
						break;
					}
				}

				readStubs(stubs, wr, ctx, true);
				type = new Struct(unordered, stubs);
				stubs.clear();
			}
			case AsnChoice -> {
				wr.except(lBrace, "{");

				readStubs(stubs, wr, ctx, false);
				type = new Choice(stubs);
				stubs.clear();
			}
		}
		return type;
	}

	private static long readSize(Asn1Tokenizer wr) throws ParseException {
		int min, max;
		wr.except(lParen, "(");

		Word w = wr.next(); // MIN or INTEGER
		if (w.val().equals("MIN")) min = 0;
		else if (w.type() == INTEGER) min = w.asInt();
		else throw wr.err("期待INTEGER(RangeMin): "+w);

		wr.except(dot2, "..");

		w = wr.next(); // MAX or INTEGER
		if (w.val().equals("MAX")) max = Integer.MAX_VALUE;
		else if (w.type() == INTEGER) max = w.asInt();
		else throw wr.err("期待INTEGER(RangeMax): "+w);

		wr.except(rParen, ")");
		if (!wr.next().val().equals("OF")) throw wr.err("期待OF(sized of)");
		return ((long)min << 32) | max;
	}

	private static void readStubs(SimpleList<MapStub> stubs, Asn1Tokenizer wr, Asn1Context ctx, boolean struct) throws ParseException {
		while (true) {
			Word w = wr.next();
			if (w.type() == rBrace) break;
			if (w.type() != LITERAL) throw wr.err("期待LITERAL(StructName) ");

			MapStub stub = new MapStub();
			stubs.add(stub);

			stub.name = w.val();
			int flag;
			w = wr.next();

			if (w.type() == lBracket) {
				w = wr.next();

				/*flag = 0;
				if (w.type() == Application) {
					w = wr.next();
					flag = 0x40;
				}*/

				if (w.asInt() > 31) throw wr.err("ContextId > 31");
				flag = w.asInt();

				wr.except(rBracket, "]");
				w = wr.next();

				if (w.type() == AsnImplicit) {
					w = wr.next();
				} else if (w.type() == AsnExplicit) {
					flag |= EXPLICIT;
					w = wr.next();
				}
			} else {
				flag = UNSPECIFIED;
			}

			stub.type = readType(w, wr, new SimpleList<>(), ctx);

			// Version DEFAULT v1,
			// 标有 DEFAULT 的字段与 OPTIONAL 类似。 如果该字段取默认值，在 BER 编码中就可以予以省略， 而在 DER 编码中则必须省略。
			w = wr.next();
			if (struct) {
				if (w.val().equals("DEFAULT")) {
					w = wr.except(LITERAL, "期待LITERAL(DefaultValue)");
					stub.defVal = w.val();
					w = wr.next();
				} else {
					if (w.type() == AsnDefinedBy) {
						stub.definedBy = wr.except(LITERAL, "DefinedBy").val();
						w = wr.next();
					}

					if (w.type() == AsnOptional) {
						flag |= OPTIONAL;
						w = wr.next();
					}
				}
			}

			stub.flag = (byte) flag;

			if (w.type() != comma) wr.retractWord();
		}
	}
}