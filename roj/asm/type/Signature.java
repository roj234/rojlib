package roj.asm.type;

import roj.asm.cst.ConstantPool;
import roj.asm.tree.attr.Attribute;
import roj.collect.LinkedMyHashMap;
import roj.collect.SimpleList;
import roj.config.word.ITokenizer;
import roj.io.IOUtil;
import roj.math.MutableInt;
import roj.text.CharList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * 泛型签名
 *
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public class Signature extends Attribute {
	public Map<String, List<IType>> typeParams;

	public static final byte METHOD = 1, FIELD = 0, CLASS = -1;

	public byte type;
	/**
	 * Contract: values[0] is extend, other is implement
	 */
	public List<IType> values;
	public List<IType> Throws;

	public static IType any() {
		return Any.I;
	}
	public static IType placeholder() {
		return EmptyClass.I;
	}

	public Signature(int type) {
		super("Signature");
		this.typeParams = Collections.emptyMap();
		this.values = Collections.emptyList();
		this.type = (byte) type;
	}

	public Signature(Map<String, List<IType>> typeParams, List<IType> value, boolean isMethod, List<IType> Throws) {
		super("Signature");
		this.typeParams = typeParams;
		this.values = value;
		this.type = (isMethod ? METHOD : (value.isEmpty() ? FIELD : CLASS));
		this.Throws = Throws;
	}

	public void validate() {
		for (Map.Entry<String, List<IType>> entry : typeParams.entrySet()) {
			List<IType> list = entry.getValue();

			for (int i = 0; i < list.size(); i++) {
				list.get(i).checkPosition(IType.TYPE_PARAMETER_ENV, i);
			}
		}

		if (type == METHOD) {
			for (int i = 0; i < values.size() - 1; i++) {
				values.get(i).checkPosition(IType.INPUT_ENV, i);
			}
			values.get(values.size() - 1).checkPosition(IType.OUTPUT_ENV, 0);

			if (Throws != null) {
				for (int i = 0; i < Throws.size(); i++) {
					Throws.get(i).checkPosition(IType.THROW_ENV, i);
				}
			}
		} else {
			for (int i = 0; i < values.size(); i++) {
				values.get(i).checkPosition(IType.FIELD_ENV, i);
			}
			if (Throws != null) {
				throw new IllegalStateException("Throws is not null in non-METHOD signature");
			}
			if (type == FIELD && values.size() != 1)
				throw new IllegalStateException("values.size() > 1 in FIELD signature");
		}
	}

	//@Override
	public String toDesc() {
		CharList sb = IOUtil.getSharedCharBuf();

		if (!typeParams.isEmpty()) {
			sb.append('<');
			for (Map.Entry<String, List<IType>> entry : typeParams.entrySet()) {
				sb.append(entry.getKey());
				List<IType> list = entry.getValue();

				list.get(0).toDesc(sb.append(':'));

				for (int i = 1; i < list.size(); i++) {
					list.get(i).toDesc(sb.append(':'));
				}
			}
			sb.append('>');
		}

		if (type == METHOD) {
			sb.append('(');
			for (int i = 0; i < values.size() - 1; i++) values.get(i).toDesc(sb);
			sb.append(')');
			values.get(values.size() - 1).toDesc(sb);

			if (Throws != null) {
				for (int i = 0; i < Throws.size(); i++) {
					sb.append('^');
					Throws.get(i).toDesc(sb);
				}
			}
		} else {
			for (int i = 0; i < values.size(); i++) {
				values.get(i).toDesc(sb);
			}
		}

		return sb.toString();
	}

	@Override
	protected void toByteArray1(DynByteBuf w, ConstantPool cp) {
		w.putShort(cp.getUtfId(toDesc()));
	}

	public String getTypeParam() {
		if (typeParams.isEmpty()) return "";

		CharList sb = IOUtil.getSharedCharBuf();

		sb.append('<');
		Iterator<Map.Entry<String, List<IType>>> itr = typeParams.entrySet().iterator();
		while (true) {
			Map.Entry<String, List<IType>> entry = itr.next();
			appendTypeParameter(sb, entry.getKey(), entry.getValue());

			if (!itr.hasNext()) break;
			sb.append(", ");
		}

		return sb.append('>').toString();
	}

	public void appendTypeParameter(CharList sb, String name, List<IType> list) {
		sb.append(name);
		if (list == null) list = typeParams.getOrDefault(name, Collections.emptyList());
		if (list.isEmpty()) return;
		if (list.size() > 1 || !list.get(0).owner().equals("java/lang/Object")) {
			sb.append(" extends ");
			int i = list.get(0).owner().equals("java/lang/Object") ? 1 : 0;
			while (true) {
				IType value = list.get(i++);
				value.toString(sb);
				if (i == list.size()) break;
				sb.append(" & ");
			}
		}
	}

	public String toString() {
		CharList sb = IOUtil.getSharedCharBuf();
		if (type == FIELD) {
			values.get(0).toString(sb);
		} else {
			if (type == METHOD) {
				toMethodString(sb, null);
			} else {
				getTypeParam();
				if (!values.get(0).owner().equals("java/lang/Object")) values.get(0).toString(sb.append(" extends "));
				if (values.size() > 1) {
					sb.append(" implements ");
					int i = 1;
					for (;;) {
						values.get(i).toString(sb);
						if (++i == values.size()) break;
						sb.append(", ");
					}
				}
			}
		}
		return sb.toString();
	}

	public void toMethodString(CharList sb, String name) {
		values.get(values.size() - 1).toString(sb);
		if (name != null) sb.append(' ').append(name);
		sb.append(' ').append('(');
		int i = 0;
		if (values.size() > 1) {
			for (;;) {
				values.get(i).toString(sb);
				if (++i == values.size()-1) break;
				sb.append(", ");
			}
		}
		sb.append(')');

		if (Throws != null && !Throws.isEmpty()) {
			sb.append(" throws ");
			i = 0;
			for(;;) {
				Throws.get(i).toString(sb);
				if (++i == Throws.size()) break;
				sb.append(", ");
			}
		}
	}

	public void rename(UnaryOperator<String> fn) {
		for (List<IType> values : typeParams.values()) {
			for (int i = values.size() - 1; i >= 0; i--) {
				values.get(i).rename(fn);
			}
		}

		if (values != null) {
			for (int i = 0; i < values.size(); i++) {
				values.get(i).rename(fn);
			}
		}
	}

	public static void main(String[] args) {
		Signature signature = parse(args[0]);
		System.out.println("toString(): " + signature);
		System.out.println("getTypeParam(): " + signature.getTypeParam());
		System.out.println("toDesc(): " + signature.toDesc());
		signature.validate();
	}

	public static Signature parse(CharSequence s) {
		return parse(s, 99);
	}

	public static Signature parse(CharSequence s, int expect) {
		MutableInt i1 = new MutableInt();
		CharList tmp = IOUtil.getSharedCharBuf();

		int i = 0;

		Signature sign = new Signature(0);

		// type parameter
		if (s.charAt(0) == '<') {
			sign.typeParams = new LinkedMyHashMap<>();
			sign.type = CLASS;

			if (expect == FIELD) error("type parameter begin('<') in FIELD type", 0, s);
			i = 1;

			while (i < s.length()) {
				int j = i;
				while (s.charAt(j) != ':') {
					j++;
					if (j >= s.length()) error("EOF before found type name end(':')", j, s);
				}
				if (i == j) error("type parameter name is empty", j, s);
				String name = s.subSequence(i,j).toString();

				// +1: skip ':'
				i = j + 1;
				SimpleList<IType> vals = new SimpleList<>(2);

				// first parameter: 'extends'
				// 'nullable': use '::' mark EmptyClass
				i1.setValue(i);
				vals.add(getSignatureValue(s, i1, F_TYPE_CLASS, tmp));
				i = i1.getValue();

				// other parameters: 'implements'
				while (s.charAt(i) == ':') {
					if (i >= s.length()) error("before type desc end( != ':')", i, s);

					i1.setValue(i+1);
					vals.add(getSignatureValue(s, i1, F_INTERFACE, tmp));
					i = i1.getValue();
				}

				if (vals.size() == 1 && vals.get(0) == EmptyClass.I)
					error("redundant EmptyClass",i,s);

				sign.typeParams.put(name, vals);

				if (s.charAt(i) == '>') {
					i++;
					break;
				}
			}
		}

		List<IType> v = sign.values = new SimpleList<>(2);

		boolean isMethod = s.charAt(i) == '(';
		if (isMethod) {
			if (expect != 99 && expect != METHOD) error("excepting " + expect + " and is METHOD", i, s);
			sign.type = METHOD;
			i1.setValue(i+1);

			// in
			while (s.charAt(i1.getValue()) != ')') {
				v.add(getSignatureValue(s, i1, F_PRIMITIVE, tmp));
			}
			i1.increment();

			// out
			v.add(getSignatureValue(s, i1, F_PRIMITIVE, tmp));

			// throws
			i = i1.getAndIncrement();
			if (i < s.length()) {
				if (s.charAt(i) != '^') error("THROWS should begin with '^'", i, s);

				v = sign.Throws = new SimpleList<>(2);
				while (i1.getValue() < s.length()) {
					v.add(getSignatureValue(s, i1, 0, tmp));
					i1.increment();
				}
			}
		} else {
			i1.setValue(i);
			// extends or field type
			v.add(getSignatureValue(s, i1, 0, tmp));

			// implements
			i = i1.getValue();
			if (i < s.length()) {
				if (expect != 99 && expect != CLASS) error("excepting " + expect + " and is CLASS", i, s);
				sign.type = CLASS;

				while (i1.getValue() < s.length()) {
					v.add(getSignatureValue(s, i1, F_INTERFACE, tmp));
				}
			}
		}

		if (sign.type != expect && expect != 99) {
			sign.type = (byte) expect;
		}

		// 当前上下文不足以检测缺失的类型参数.
		return sign;
	}

	public static IType parseGeneric(CharSequence s) {
		return getSignatureValue(s, new MutableInt(), 0, IOUtil.getSharedCharBuf());
	}

	static void error(String msg, int i, CharSequence s) {
		throw new IllegalArgumentException("[" + i + "(" + (i>=s.length()? "EOF":s.charAt(i)) + ")]" + msg + " in " + ITokenizer.addSlashes(s));
	}

	private static final int F_INTERFACE = 0x1000, F_PRIMITIVE = 0x2000, F_SUBCLASS = 0x4000, F_TYPE_CLASS = 0x8000;

	private static IType getSignatureValue(CharSequence s, MutableInt mi, int flag, CharList tmp) {
		tmp.clear();
		int i = mi.getValue();

		int array = i;
		while (s.charAt(i) == '[') i++;
		array = i-array;

		char c = s.charAt(i);
		mi.setValue(i+1);
		switch (c) {
			case ':':
				if ((flag & F_TYPE_CLASS) == 0)
					error("unexpected ':', not F_TYPE_CLASS flag",i,s);
				mi.decrement();
				return EmptyClass.I;

			case 'L':
				return genericUse(s,mi,tmp,array|(flag&0xF00));

			case 'T':
				c = _getDesc(s,mi,tmp);
				if (c != ';') error("excepting ';' but got " + c,mi.getValue()-1,s);
				return new TypeParam(tmp.toString(), array, (flag >>> 8) & 0xF);

			default:
				if ((flag & F_PRIMITIVE) == 0)
					error("flag PRIMITIVE is not set("+flag+")",i,s);
				try {
					return array == 0 ? Type.std(c) : new Type(c, array);
				} catch (Exception e) {
					error("not valid type: " + c,i,s);
					return Helpers.nonnull();
				}
		}
	}

	private static IType genericUse(CharSequence s, MutableInt mi, CharList tmp, int flag) {
		char c = _getDesc(s, mi, tmp);
		int pos = mi.getValue();

		IGeneric g;
		if ((flag & F_SUBCLASS) != 0) {
			g = new GenericSub(tmp.toString());
		} else if ((flag & 0xF00) != 0) {
			g = new Generic(tmp.toString(), flag&0xFF, (byte) ((flag >>> 8)&0xF));
		} else g = null;

		if (c == '<') {
			if (g == null) g = new Generic(tmp.toString(), flag&0xFF, (byte) ((flag >>> 8)&0xF));

			childrenLoop:
			while (true) {
				if (pos >= s.length()) error("在参数结束之前",pos,s);
				c = s.charAt(pos);
				switch (c) {
					case '>':
						if (g.children.isEmpty()) error("空参数列表", pos, s);
						pos++;
						break childrenLoop;
					case '*':
						pos++;
						g.addChild(Any.I);
						break;
					default:
						int ex;
						switch (c) {
							case '+':
								pos++;
								ex = Generic.EX_EXTENDS << 8;
								break;
							case '-':
								pos++;
								ex = Generic.EX_SUPER << 8;
								break;
							default:
								ex = 0;
								break;
						}
						mi.setValue(pos);
						g.addChild(getSignatureValue(s, mi, F_PRIMITIVE | ex, tmp));
						pos = mi.getValue();
						break;
				}
			}
			c = s.charAt(pos);
			mi.setValue(pos+1);
		}

		if (c == '.') {
			if (g == null) g = new Generic(tmp.toString(), flag&0xFF, (byte) ((flag >>> 8)&0xF));

			g.sub = (GenericSub) genericUse(s, mi, tmp, F_SUBCLASS);
		} else if (c != ';') error("未预料的 '"+c+"' 期待';'或'.'",pos,s);

		if (g != null) return g;
		return new Type(tmp.toString(), flag&0xFF);
	}

	private static char _getDesc(CharSequence s, MutableInt mi, CharList tmp) {
		int j = mi.getValue();
		while (true) {
			switch (s.charAt(j)) {
				case ';':
				case '.':
				case '<':
					if (mi.getValue() == j)
						error("类名为空",j,s);
					tmp.clear();
					tmp.append(s,mi.getValue(),j);
					mi.setValue(j+1);
					return s.charAt(j);
			}
			j++;
			if (j >= s.length()) error("在类名结束之前 (';')",j,s);
		}
	}
}