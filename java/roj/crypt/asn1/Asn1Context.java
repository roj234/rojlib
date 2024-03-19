package roj.crypt.asn1;

import roj.NativeLibrary;
import roj.collect.*;
import roj.config.ParseException;
import roj.config.Word;
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
 * @since 2024/3/22 0022 22:58
 */
public class Asn1Context {
	protected final Map<String, Type> map = new MyHashMap<>();
	private final MyHashSet<DerValue> byVal = new MyHashSet<>();

	@Override
	public String toString() {
		return "Asn1Context{" + "map=" + map + ", namedOid=" + byVal + '}';
	}

	static final ThreadLocal<Asn1Context> CURRENT_PARSING = new ThreadLocal<>();
	public DerValue parse(String struct, DerReader in) throws IOException {
		Type type = map.get(struct);

		CURRENT_PARSING.set(this);
		try {
			return type.parse(in.readType(), in);
		} finally {
			CURRENT_PARSING.remove();
		}
	}
	public void write(String struct, DerValue value, DerWriter out) {
		// TODO
	}

	protected Type unmarshalAny(String struct, String target, DerValue value) {
		if (struct.equals("ContentInfo")) {
			Object oid = byVal.find1(value);
			if (oid == IntMap.UNDEFINED) throw new UnsupportedOperationException("未知的OID "+value);
			String name = ((NamedOID)oid).name;
			return map.get(name);
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
			int[] _oid = ((DerValue.OID) value).oid;
			if (_oid[0] == 2 && _oid[1] == 5 && _oid[2] == 4)
				return Simple.PrintableString;
		}

		if (NativeLibrary.EXTRA_BUG_CHECK) System.err.println("无法获取 "+struct+"."+target+"的类型，通过已知信息 "+value);
		return null;
	}

	static final class NamedOID extends DerValue.OID {
		final String name;
		NamedOID(String name, int... oid) {
			super(oid);
			this.name = name;
		}
		@Override
		public String toString() { return "OID["+name+"] = ["+Arrays.toString(oid)+"]"; }
	}

	static final byte UNSPECIFIED = 0b00111111, OPTIONAL = (byte) 128, EXPLICIT = 64;

	public sealed interface Type permits Simple, BaseType {
		int derType();
		CharList append(CharList sb, int prefix);
		DerValue parse(int type, DerReader in) throws IOException;
		default void setName(String name) {};
		default DerValue translate(String name) { throw new UnsupportedOperationException(getClass().getName()+"无法解析"+name); }
	}
	sealed abstract static class BaseType implements Type permits Alias, Choice, Enum, List, Range, Struct {
		@Override
		public final String toString() { return append(IOUtil.getSharedCharBuf(), 0).toString(); }
	}
	public enum Simple implements Type {
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
		public DerValue parse(int type, DerReader in) throws IOException {
			int len = in.readLength();
			return switch (this) {
				case INTEGER -> in.readInt(len);
				case BIT_STRING -> in.readBits(len);
				case OCTET_STRING -> in.readBytes(len);
				case NULL -> DerValue.Null.NUL;
				case UTF8_STRING -> in.readUTF(len);
				case PrintableString, IA5_STRING, UTCTime, GeneralizedTime -> in.readStr(derType, len);
				case OID -> in.readOid(len);
				case ANY -> in.readUnp(type, len);
				case BOOLEAN -> in.readInt(len); // should be 1
			};
		}
		public DerValue translate(String name) {
			return switch (this) {
				case INTEGER -> new DerValue.Int(new BigInteger(name));
				default -> Type.super.translate(name);
			};
		}
	}
	/**
	 * X { name(value) }
 	 */
	static final class Enum extends BaseType {
		byte original;
		String[] names;
		DerValue[] values;

		public Enum(Type type, SimpleList<MapStub> stubs) {
			original = (byte) ((Simple) type).ordinal();
			int len = stubs.size();
			names = new String[len];
			values = new DerValue[len];
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
		public DerValue parse(int type, DerReader in) throws IOException {
			DerValue value = Simple.VALUES[original].parse(type, in);
			for (DerValue v1 : values) {
				if (value.equals(v1)) return value;
			}
			throw new CorruptedInputException("value("+value+") not in enum "+this);
		}
		@Override
		public DerValue translate(String name) {
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
	static final class Choice extends BaseType {
		String[] names;
		Type[] choices;
		byte[] rules;
		CharMap<Type> mappedTarget;

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
					sb.append('[').append(flag&63).append("] ")
					  .append((flag & EXPLICIT) == 0 ? "IMPLICIT " : "EXPLICIT ");
				}
				choices[i].append(sb, prefix+1);
				if (++i == choices.length) return sb.append('\n').padEnd('\t', prefix).append('}');
				sb.append(", \n");
			}
		}
		public DerValue parse(int type, DerReader in) throws IOException {
			Type type1 = mappedTarget.get((char)type);
			if (type1 == null) throw new CorruptedInputException("选项("+type+")不在范围中:"+this);
			if ((type&EXPLICIT) != 0) {
				in.readLength();
				type = in.readType();
			}
			return type1.parse(type, in);
		}
	}
	/**
	 * A ::= B
	 */
	static final class Alias extends BaseType {
		private final String alias;
		private final Type original;
		Alias(String alias, Type original) { this.alias = alias; this.original = original; }
		public int derType() { return original.derType(); }
		public CharList append(CharList sb, int prefix) {return sb.append(alias).append(" (ref ").append(original.getClass().getSimpleName()).append(")");}
		public DerValue parse(int type, DerReader in) throws IOException {return original.parse(type, in);}
		public DerValue translate(String name) { return original.translate(name); }
	}
	/**
	 * SIZE (1..MAX)
	 */
	static final class Range extends BaseType {
		private final Type original;
		private final int min, max;
		Range(Type original, int min, int max) { this.original = original; this.min = min; this.max = max; }
		public int derType() { return original.derType(); }
		public CharList append(CharList sb, int prefix) {return original.append(sb.append("SIZE (").append(min).append("...").append(max).append(") "), prefix);}
		public DerValue parse(int type, DerReader in) throws IOException {
			DerValue value = original.parse(type, in);
			if (value instanceof DerValue.Coll c) {
				if (c.list.size() < min || c.list.size() > max) throw new CorruptedInputException("值("+value+") 超出范围: "+this);
			} else if (value instanceof DerValue.Str s) {
				if (s.text.length() < min || s.text.length() > max) throw new CorruptedInputException("值("+value+") 超出范围: "+this);
			} else {
				throw new CorruptedInputException("值("+value+") 无法定义范围: "+this);
			}
			return value;
		}
	}
	/**
	 * SET OF
	 */
	static final class List extends BaseType {
		private final boolean chaos;
		private final Type type;
		List(boolean chaos, Type type) { this.chaos = chaos; this.type = type; }
		public int derType() { return chaos?DerValue.SET:DerValue.SEQUENCE; }
		public CharList append(CharList sb, int prefix) {return type.append(sb.append(chaos?"SET":"SEQUENCE").append(" OF["), prefix).append(']');}
		public DerValue parse(int type, DerReader in) throws IOException {
			int end = in.readLength() + in.position();
			SimpleList<DerValue> values = new SimpleList<>();
			while (in.position() < end) values.add(this.type.parse(in.readType(), in));

			if (in.position() != end) throw new CorruptedInputException("length mismatch");
			return new DerValue.Coll(chaos ? DerValue.SET : DerValue.SEQUENCE, values);
		}
	}
	/**
	 * SEQUENCE
	 */
	static final class Struct extends BaseType {
		String name;
		String[] names;
		Type[] types;
		byte[] flags;
		Object[] context;
		boolean chaos;

		public Struct(boolean chaos, SimpleList<MapStub> stubs) {
			this.chaos = chaos;
			int len = stubs.size();
			names = new String[len];
			types = new Type[len];
			flags = new byte[len];
			context = new Object[len];
			for (int i = 0; i < len; i++) {
				MapStub stub = stubs.get(i);
				names[i] = stub.name;
				types[i] = stub.type;
				flags[i] = stub.flag;
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
		public int derType() { return chaos?DerValue.SET:DerValue.SEQUENCE; }
		public CharList append(CharList sb, int prefix) {
			sb.append(chaos ? "SET { (" : "SEQUENCE { (").append(name).append(")\n");
			int i = 0;
			while (true) {
				sb.padEnd('\t', prefix+1).append(names[i]).append(' ');
				int flag = flags[i];
				if (flag != UNSPECIFIED && flag != OPTIONAL) {
					sb.append('[').append(flag&63).append("] ")
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
		public DerValue parse(int type, DerReader in) throws IOException {
			if (chaos) throw new UnsupportedOperationException("sorry, but "+this+" is not parsable at this time!");
			if (names.length == 0) return Simple.ANY.parse(type, in);

			int end = in.readLength()+in.position();
			SimpleList<DerValue> values = new SimpleList<>();
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
									ctx instanceof DerValue d ? d :
									(DerValue) (context[i-1] = target.translate(ctx.toString())));
						continue;
					}

					if (in.position() >= end) break;

					type = in.readType();
				}
			}

			if (in.position() != end) throw new CorruptedInputException("length mismatch, pos="+in.position()+", except="+end);
			return new DerValue.Coll(chaos?DerValue.SET:DerValue.SEQUENCE, name, names, values);
		}
		public void setName(String name) { this.name = name; }
	}

	static final class MapStub {
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
								oids.addAll(ref.oid);
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
				if (original == null) {
					if (NativeLibrary.EXTRA_BUG_CHECK) System.err.println("无法找到引用："+w.val());
					original = Simple.ANY;
				}
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
			int flag = UNSPECIFIED;
			w = wr.next();

			if (w.type() == lBracket) {
				flag = wr.except(INTEGER, "ContextId").asInt();
				if (flag == UNSPECIFIED) throw wr.err("为了省点内存，1/256的报应来了吧！");

				wr.except(rBracket, "]");
				w = wr.next();
			}

			if (w.type() == AsnImplicit) {
				w = wr.next();
			} else if (w.type() == AsnExplicit) {
				flag |= EXPLICIT;
				w = wr.next();
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