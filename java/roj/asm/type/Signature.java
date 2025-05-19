package roj.asm.type;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import roj.asm.AsmCache;
import roj.asm.attr.Attribute;
import roj.asm.cp.ConstantPool;
import roj.collect.LinkedMyHashMap;
import roj.collect.SimpleList;
import roj.config.Tokenizer;
import roj.config.data.CInt;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * 签名属性，可以是类，方法或字段的泛型签名，包括它定义和使用的类型参数.
 * 各个字段的状态：
 * 类型为Class时:
 *  type = -1
 *  typeParams = 该类定义的类型参数
 * 	values 第0项为extends的泛型类型，后续项为implements
 * 	exceptions 空
 * 类型为Field时
 *  type = 0
 *  typeParams = 空
 *  values 长度固定为1 为该字段的泛型类型
 *  exceptions 空
 * 类型为Method时：
 *  type = 1
 *  typeParams = 该方法定义的类型参数
 *  values 最后一项为返回值类型 前面各项为参数类型
 *  exceptions 抛出的泛型异常
 *
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public class Signature extends Attribute {
	public static final int METHOD = 1, FIELD = 0, CLASS = -1;
	@MagicConstant(intValues = {METHOD, FIELD, CLASS}) public byte type;
	@NotNull public Map<String, List<IType>> typeParams;
	@NotNull public List<IType> values, exceptions;

	public static IType any() { return Any.I; }
	public static IType placeholder() { return Placeholder.I; }

	public Signature(@MagicConstant(intValues = {METHOD, FIELD, CLASS}) int type) {
		this.type = (byte) type;
		this.typeParams = Collections.emptyMap();
		this.values = Collections.emptyList();
		this.exceptions = Collections.emptyList();
	}

	public void validate() {
		for (Map.Entry<String, List<IType>> entry : typeParams.entrySet()) {
			List<IType> list = entry.getValue();

			for (int i = 0; i < list.size(); i++) {
				list.get(i).validate(IType.TYPE_PARAMETER_ENV, i);
			}
		}

		if (type == METHOD) {
			for (int i = 0; i < values.size() - 1; i++) {
				values.get(i).validate(IType.INPUT_ENV, i);
			}
			values.get(values.size() - 1).validate(IType.OUTPUT_ENV, 0);

			for (int i = 0; i < exceptions.size(); i++) {
				exceptions.get(i).validate(IType.THROW_ENV, i);
			}
		} else {
			for (int i = 0; i < values.size(); i++) {
				values.get(i).validate(IType.INPUT_ENV, i);
			}
			if (!exceptions.isEmpty()) throw new IllegalStateException("非方法泛型不能定义抛出异常");
			if (type == FIELD && values.size() != 1) throw new IllegalStateException("字段泛型只能有一项值");
		}
	}

	@Override public String name() {return "Signature";}
	@Override public void toByteArrayNoHeader(DynByteBuf w, ConstantPool cp) {w.putShort(cp.getUtfId(toDesc()));}

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
			values.get(values.size() - 1).toDesc(sb.append(')'));

			for (int i = 0; i < exceptions.size(); i++) {
				exceptions.get(i).toDesc(sb.append('^'));
			}
		} else {
			for (int i = 0; i < values.size(); i++) {
				values.get(i).toDesc(sb);
			}
		}

		return sb.toString();
	}

	public String toString() {
		var sb = IOUtil.getSharedCharBuf();
		if (type == FIELD) {
			values.get(0).toString(sb);
		} else {
			if (type == METHOD) {
				toMethodString(sb, "<some method>");
			} else {
				getTypeParam(sb);
				if (!"java/lang/Object".equals(values.get(0).owner())) values.get(0).toString(sb.append(" extends "));
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

		if (!exceptions.isEmpty()) {
			sb.append(" throws ");
			i = 0;
			for(;;) {
				exceptions.get(i).toString(sb);
				if (++i == exceptions.size()) break;
				sb.append(", ");
			}
		}
	}
	public CharList getTypeParam(CharList sb) {
		if (typeParams.isEmpty()) return sb;

		sb.append('<');
		Iterator<Map.Entry<String, List<IType>>> itr = typeParams.entrySet().iterator();
		while (true) {
			Map.Entry<String, List<IType>> entry = itr.next();
			appendTypeParameter(sb, entry.getKey(), entry.getValue());

			if (!itr.hasNext()) break;
			sb.append(", ");
		}

		return sb.append('>');
	}
	private void appendTypeParameter(CharList sb, String name, List<IType> list) {
		sb.append(name);
		if (list == null) list = typeParams.getOrDefault(name, Collections.emptyList());
		if (list.isEmpty()) return;
		if (list.size() > 1 || !"java/lang/Object".equals(list.get(0).owner())) {
			sb.append(" extends ");
			int i = "java/lang/Object".equals(list.get(0).owner()) ? 1 : 0;
			while (true) {
				IType value = list.get(i++);
				value.toString(sb);
				if (i == list.size()) break;
				sb.append(" & ");
			}
		}
	}

	public void rename(UnaryOperator<String> fn) {
		for (List<IType> values : typeParams.values()) {
			for (int i = values.size() - 1; i >= 0; i--) {
				values.get(i).rename(fn);
			}
		}

		for (int i = 0; i < values.size(); i++) {
			values.get(i).rename(fn);
		}
	}

	public static void main(String[] args) {
		var signature = parse(args[0]);
		System.out.println("getTypeParam(): "+signature.getTypeParam(IOUtil.getSharedCharBuf()).toString());
		System.out.println("toString(): "+signature);
		System.out.println("toDesc(): "+signature.toDesc());
		signature.validate();
	}

	public static Signature parse(CharSequence s) {return parse(s, 99);}
	public static Signature parse(CharSequence s, @MagicConstant(intValues = {CLASS, FIELD, METHOD, 99/*ANY*/}) int expect) {
		CInt i1 = new CInt();
		CharList tmp = IOUtil.getSharedCharBuf();

		int i = 0;

		Signature sign = new Signature(FIELD);

		// type parameter
		if (s.charAt(0) == '<') {
			sign.typeParams = new LinkedMyHashMap<>();
			sign.type = CLASS;

			if (expect == FIELD) fail("未预料的<类型参数>(预期类型是<字段>)", 0, s);
			i = 1;

			while (i < s.length()) {
				int j = i;
				while (s.charAt(j) != ':') {
					j++;
					if (j >= s.length()) fail("未结束的类型参数名称", j, s);
				}
				if (i == j) fail("类型参数名称为空", j, s);
				String name = s.subSequence(i,j).toString();

				// +1: skip ':'
				i = j + 1;
				SimpleList<IType> vals = new SimpleList<>(2);

				// first parameter: 'extends'
				// 'nullable': use '::' mark EmptyClass
				i1.value = i;
				vals.add(parseValue(s, i1, F_PLACEHOLDER, tmp));
				i = i1.value;

				// other parameters: 'implements'
				while (s.charAt(i) == ':') {
					if (i >= s.length()) fail("未结束的'和'类型", i, s);

					i1.value = i+1;
					vals.add(parseValue(s, i1, 0, tmp));
					i = i1.value;
				}

				if (vals.size() == 1 && vals.get(0) == Placeholder.I) fail("此处不允许占位符类型",i,s);

				sign.typeParams.put(name, vals);

				if (s.charAt(i) == '>') {
					i++;
					break;
				}
			}
		}

		List<IType> v = AsmCache.getInstance().methodTypeTmp();
		boolean isMethod = s.charAt(i) == '(';
		if (isMethod) {
			if (expect != 99 && expect != METHOD) fail("未预料的<方法参数>(预期类型是"+expect+")", i, s);
			sign.type = METHOD;
			i1.value = i+1;

			// in
			while (s.charAt(i1.value) != ')') {
				v.add(parseValue(s, i1, F_PRIMITIVE, tmp));
			}
			i1.value++;

			// out
			v.add(parseValue(s, i1, F_PRIMITIVE, tmp));

			sign.values = new SimpleList<>(v);

			// throws
			i = i1.value++;
			if (i < s.length()) {
				if (s.charAt(i) != '^') fail("throws应以^开始", i, s);

				v.clear();
				while (i1.value < s.length()) {
					v.add(parseValue(s, i1, 0, tmp));
					i1.value++;
				}

				sign.exceptions = new SimpleList<>(v);
			}
		} else {
			i1.value = i;
			// extends or field type
			v.add(parseValue(s, i1, 0, tmp));

			// implements
			i = i1.value;
			if (i < s.length()) {
				if (expect != 99 && expect != CLASS) fail("未预料的implements(预期类型是"+expect+")", i, s);
				sign.type = CLASS;

				while (i1.value < s.length()) {
					v.add(parseValue(s, i1, 0, tmp));
				}
			}

			sign.values = new SimpleList<>(v);
		}

		if (sign.type != expect && expect != 99) {
			sign.type = (byte) expect;
		}

		// 当前上下文不足以检测缺失的类型参数.
		return sign;
	}

	public static IType parseGeneric(CharSequence s) {return parseValue(s, new CInt(), 0, IOUtil.getSharedCharBuf());}

	private static final int F_PLACEHOLDER = 0x1000, F_PRIMITIVE = 0x2000, F_SUBCLASS = 0x4000;
	private static IType parseValue(CharSequence s, CInt mi, int flag, CharList tmp) {
		tmp.clear();
		int i = mi.value;

		int array = i;
		while (s.charAt(i) == '[') i++;
		array = i-array;

		char c = s.charAt(i);
		mi.value = i+1;
		switch (c) {
			case ':':
				if ((flag & F_PLACEHOLDER) == 0) fail("此处不允许占位符类型("+flag+")",i,s);
				mi.value--;
				return Placeholder.I;

			case 'L':
				return parseType(s,mi,tmp,array|(flag&0xF00));

			case 'T':
				c = parseTypeParam(s,mi,tmp);
				if (c != ';') fail("未结束的类型"+c, mi.value-1,s);
				return new TypeParam(tmp.toString(), array, (flag >>> 8) & 0xF);

			default:
				if ((flag & F_PRIMITIVE) == 0) fail("此处不允许基本类型("+flag+")",i,s);
				try {
					return array == 0 ? Type.primitive(c) : Type.primitive(c, array);
				} catch (Exception e) {
					fail("无效的类型: "+e.getMessage(),i,s);
					return Helpers.nonnull();
				}
		}
	}
	private static IType parseType(CharSequence s, CInt mi, CharList tmp, int flag) {
		char c = parseTypeParam(s, mi, tmp);
		int pos = mi.value;

		IGeneric g;
		if ((flag & F_SUBCLASS) != 0) {
			g = new GenericSub(tmp.toString());
		} else if ((flag & 0xF00) != 0 || c == '<') {
			g = new Generic(tmp.toString(), flag&0xFF, (byte) ((flag >>> 8)&0xF));
		} else g = null;

		if (c == '<') {
			childrenLoop:
			while (true) {
				if (pos >= s.length()) fail("未结束的类型列表",pos,s);
				c = s.charAt(pos);
				switch (c) {
					case '>':
						if (g.children.isEmpty()) fail("空类型列表", pos, s);
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
						mi.value = pos;
						g.addChild(parseValue(s, mi, F_PRIMITIVE | ex, tmp));
						pos = mi.value;
						break;
				}
			}
			c = s.charAt(pos);
			mi.value = pos+1;
		}

		if (c == '.') {
			if (g == null) g = new Generic(tmp.toString(), flag&0xFF, (byte) ((flag >>> 8)&0xF));

			g.sub = (GenericSub) parseType(s, mi, tmp, F_SUBCLASS);
		} else if (c != ';') fail("未预料的 '"+c+"' 期待';'或'.'",pos,s);

		if (g != null) return g;
		return Type.klass(tmp.toString(), flag & 0xFF);
	}
	private static char parseTypeParam(CharSequence s, CInt mi, CharList tmp) {
		int j = mi.value;
		while (true) {
			switch (s.charAt(j)) {
				case ';':
				case '.':
				case '<':
					if (mi.value == j)
						fail("类名为空",j,s);
					tmp.clear();
					tmp.append(s, mi.value,j);
					mi.value = j+1;
					return s.charAt(j);
			}
			j++;
			if (j >= s.length()) fail("在类名结束之前 (';')",j,s);
		}
	}
	private static void fail(String error, int position, CharSequence signature) {throw new IllegalArgumentException(Tokenizer.escape(signature)+" 在第"+position+"个字符("+(position>=signature.length()?"EOF":signature.charAt(position))+")解析失败: "+error);}
}