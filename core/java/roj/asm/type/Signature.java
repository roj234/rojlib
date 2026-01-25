package roj.asm.type;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.AsmCache;
import roj.asm.attr.Attribute;
import roj.asm.cp.ConstantPool;
import roj.collect.ArrayList;
import roj.collect.LinkedOpenHashKVSet;
import roj.config.node.IntValue;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.Tokenizer;
import roj.util.DynByteBuf;
import roj.util.OperationDone;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * 签名属性，可以是类，方法或字段的泛型签名，包括它定义和使用的类型参数.
 * 各个字段的状态：
 * 类型为Class时:
 *  type = -1
 *  typeVariables = 该类定义的类型参数
 * 	values 第0项为extends的泛型类型，后续项为implements
 * 	exceptions 空
 * 类型为Field时
 *  type = 0
 *  typeVariables = 空
 *  values 长度固定为1 为该字段的泛型类型
 *  exceptions 空
 * 类型为Method时：
 *  type = 1
 *  typeVariables = 该方法定义的类型参数
 *  values 最后一项为返回值类型 前面各项为参数类型
 *  exceptions 抛出的泛型异常
 *
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public class Signature extends Attribute implements TypeVariableContext {
	public static final int METHOD = 1, FIELD = 0, CLASS = -1;
	@MagicConstant(intValues = {METHOD, FIELD, CLASS}) public byte type;
	@NotNull public LinkedOpenHashKVSet<String, TypeVariableDeclaration> typeVariables;
	@NotNull public List<IType> values, exceptions;

	public static IType unboundedWildcard() { return Any.any; }
	public static IType objectBound() { return Itf.itf; }

	public Signature(@MagicConstant(intValues = {METHOD, FIELD, CLASS}) int type) {
		this.type = (byte) type;
		this.typeVariables = LinkedOpenHashKVSet.emptySet();
		this.values = Collections.emptyList();
		this.exceptions = Collections.emptyList();
	}

	public void validate() {
		for (var decl : typeVariables) {
			for (int i = 0; i < decl.size(); i++) {
				decl.get(i).validate(IType.E_TYPE_VARIABLE, i);
			}
		}

		if (type == METHOD) {
			for (int i = 0; i < values.size() - 1; i++) {
				values.get(i).validate(IType.E_ARGUMENT, i);
			}
			values.get(values.size() - 1).validate(IType.E_RETURN, 0);

			for (int i = 0; i < exceptions.size(); i++) {
				exceptions.get(i).validate(IType.E_THROW, i);
			}
		} else {
			for (int i = 0; i < values.size(); i++) {
				values.get(i).validate(IType.E_ARGUMENT, i);
			}
			if (!exceptions.isEmpty()) throw new IllegalStateException("非方法泛型不能定义抛出异常");
			if (type == FIELD && values.size() != 1) throw new IllegalStateException("字段泛型只能有一项值");
		}
	}

	/**
	 * 返回若当前typeVariables的上界(擦除后的类型)格式为typeParameter
	 * @return
	 */
	@NotNull
	public List<IType> getBounds() {
		List<IType> bounds = new ArrayList<>(typeVariables.size());
		for (var decl : typeVariables) {
			IType type = decl.get(0);
			if (type.kind() == IType.OBJECT_BOUND) type = decl.get(1);

			bounds.add(type);
		}
		return bounds;
	}

	@Override public String name() {return "Signature";}
	@Override public void toByteArrayNoHeader(DynByteBuf w, ConstantPool cp) {w.putShort(cp.getUtfId(toDesc()));}

	//@Override
	public String toDesc() {
		CharList sb = IOUtil.getSharedCharBuf();

		if (!typeVariables.isEmpty()) {
			sb.append('<');
			for (var decl : typeVariables) {
				sb.append(decl.name);

				decl.get(0).toDesc(sb.append(':'));

				for (int i = 1; i < decl.size(); i++) {
					decl.get(i).toDesc(sb.append(':'));
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
				toMethodString(sb, "#NAME");
			} else {
				getTypeVariables(sb);
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
		sb.append(' ');
		if (name != null) sb.append(name);
		sb.append('(');
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
	public CharList getTypeVariables(CharList sb) {
		if (typeVariables.isEmpty()) return sb;

		sb.append('<');
		var itr = typeVariables.iterator();
		while (true) {
			var decl = itr.next();

			sb.append(decl.name);
			if (!decl.isEmpty()) {
				if (decl.size() > 1 || !"java/lang/Object".equals(decl.get(0).owner())) {
					sb.append(" extends ");
					int i = "java/lang/Object".equals(decl.get(0).owner()) ? 1 : 0;
					while (true) {
						decl.get(i).toString(sb);
						if (++i == decl.size()) break;
						sb.append(" & ");
					}
				}
			}

			if (!itr.hasNext()) break;
			sb.append(", ");
		}

		return sb.append('>');
	}

	public void rename(UnaryOperator<String> fn) {
		for (var values : typeVariables) {
			for (int i = values.size() - 1; i >= 0; i--) {
				values.get(i).rename(fn);
			}
		}

		for (int i = 0; i < values.size(); i++) {
			values.get(i).rename(fn);
		}
	}

	public static Signature parse(CharSequence s) {return parse(s, 99);}
	public static Signature parse(CharSequence s, @MagicConstant(intValues = {CLASS, FIELD, METHOD, 99/*ANY*/}) int expect) {return parse(s, expect, null);}
	public static Signature parse(CharSequence s, @MagicConstant(intValues = {CLASS, FIELD, METHOD, 99/*ANY*/}) int expect, Signature parent) {
		IntValue i1 = new IntValue();
		CharList tmp = IOUtil.getSharedCharBuf();

		int i = 0;

		Signature sign = new Signature(FIELD);

		// type parameter
		if (s.charAt(0) == '<') {
			var declaredTypeVariables = new ArrayList<TypeVariableDeclaration>();
			var usedTypeVariables = new TVDSet();
			sign.type = CLASS;

			TypeVariableContext ctx = name -> {
				var decl = usedTypeVariables.get(name);
				if (decl == null) usedTypeVariables.add(decl = TypeVariableDeclaration.newUnresolved(name));
				return decl;
			};

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

				var decl = usedTypeVariables.get(name);
				if (decl == null) {
					usedTypeVariables.add(decl = new TypeVariableDeclaration(name));
				} else {
					assert decl.getState() == 1;
					decl.clearUnresolved();
					decl.clear();
				}
				declaredTypeVariables.add(decl);

				// first parameter: 'extends'
				// 'nullable': use '::' mark EmptyClass
				i1.value = i;
				decl.add(parseValue(s, i1, F_PLACEHOLDER, tmp, ctx));
				i = i1.value;

				// other parameters: 'implements'
				while (s.charAt(i) == ':') {
					if (i >= s.length()) fail("未结束的'和'类型", i, s);

					i1.value = i+1;
					decl.add(parseValue(s, i1, 0, tmp, ctx));
					i = i1.value;
				}

				if (decl.size() == 1 && decl.get(0) == Itf.itf) fail("此处不允许占位符类型",i,s);

				if (s.charAt(i) == '>') { i++; break; }
			}

			if (parent != null) {
				for (var tvd : declaredTypeVariables) usedTypeVariables.remove(tvd);
				for (var tvd : usedTypeVariables) {
					TypeVariableDeclaration decl = parent.typeVariables.get(tvd.name);
					if (decl != null) {
						for (IType user : tvd) ((TypeVariable) user).decl = decl;
						tvd.clear();
					} else {
						throw new IllegalArgumentException("找不到类型参数 "+tvd.name);
					}
				}
			}

			// 这样的话就不需要清除hashMap了
			if (!declaredTypeVariables.equals(usedTypeVariables.getItems())) {
				usedTypeVariables.setItems(declaredTypeVariables);
			}
			sign.typeVariables = usedTypeVariables;
		}

		var ctx = parent == null ? sign : sign.withParent(parent);
		List<IType> v = AsmCache.getInstance().methodTypeTmp();
		boolean isMethod = s.charAt(i) == '(';
		if (isMethod) {
			if (expect != 99 && expect != METHOD) fail("未预料的<方法参数>(预期类型是"+expect+")", i, s);
			sign.type = METHOD;
			i1.value = i+1;

			// in
			while (s.charAt(i1.value) != ')') {
				v.add(parseValue(s, i1, F_PRIMITIVE, tmp, ctx));
			}
			i1.value++;

			// out
			v.add(parseValue(s, i1, F_PRIMITIVE, tmp, ctx));

			sign.values = new ArrayList<>(v);

			// throws
			i = i1.value++;
			if (i < s.length()) {
				if (s.charAt(i) != '^') fail("throws应以^开始", i, s);

				v.clear();
				while (i1.value < s.length()) {
					v.add(parseValue(s, i1, 0, tmp, ctx));
					i1.value++;
				}

				sign.exceptions = new ArrayList<>(v);
			}
		} else {
			i1.value = i;
			// extends or field type
			v.add(parseValue(s, i1, 0, tmp, ctx));

			// implements
			i = i1.value;
			if (i < s.length()) {
				if (expect != 99 && expect != CLASS) fail("未预料的implements(预期类型是"+expect+")", i, s);
				sign.type = CLASS;

				while (i1.value < s.length()) {
					v.add(parseValue(s, i1, 0, tmp, ctx));
				}
			}

			sign.values = new ArrayList<>(v);
		}

		if (sign.type != expect && expect != 99) {
			sign.type = (byte) expect;
		}

		return sign;
	}

	public static IType parseGeneric(CharSequence s) {return parseGeneric(s, TypeVariableContext.EMPTY);}
	public static IType parseGeneric(CharSequence s, TypeVariableContext ctx) {return parseValue(s, new IntValue(), F_PRIMITIVE, IOUtil.getSharedCharBuf(), ctx);}

	private static final int F_PLACEHOLDER = 0x1000, F_PRIMITIVE = 0x2000, F_SUBCLASS = 0x4000;
	@SuppressWarnings("MagicConstant")
	private static IType parseValue(CharSequence s, IntValue pOffset, int flag, CharList tmp, TypeVariableContext ctx) {
		tmp.clear();
		int i = pOffset.value;

		int array = i;
		while (s.charAt(i) == '[') i++;
		array = i-array;

		char c = s.charAt(i);
		pOffset.value = i+1;
		switch (c) {
			case ':':
				if ((flag & F_PLACEHOLDER) == 0) fail("此处不允许占位符类型("+flag+")",i,s);
				pOffset.value--;
				return Itf.itf;

			case 'L':
				return parseType(s,pOffset,tmp,array|(flag&0xF00), ctx);

			case 'T':
				c = parseTypeParam(s,pOffset,tmp);
				if (c != ';') fail("未结束的类型"+c, pOffset.value-1,s);
				String name = tmp.toString();

				TypeVariableDeclaration decl = ctx.resolveTypeVariable(name);
				if (decl == null) {
					decl = new TypeVariableDeclaration(name, 0);
					decl.state = 2;
				}

				return new TypeVariable(decl, array, (flag >>> 8) & 0xF);

			default:
				if ((flag & F_PRIMITIVE) == 0) fail("此处不允许基本类型("+flag+")",i,s);
				try {
					return array == 0 ? Type.primitive(c) : Type.primitive(c, array);
				} catch (Exception e) {
					fail("无效的类型: "+e.getMessage(),i,s);
					throw OperationDone.NEVER;
				}
		}
	}
	@SuppressWarnings("MagicConstant")
	private static IType parseType(CharSequence s, IntValue pOffset, CharList tmp, int flag, TypeVariableContext ctx) {
		char c = parseTypeParam(s, pOffset, tmp);
		int pos = pOffset.value;

		IGeneric g;
		if ((flag & F_SUBCLASS) != 0) {
			g = new GenericSub(tmp.toString());
		} else if ((flag & 0xF00) != 0 || c == '<') {
			g = new ParameterizedType(tmp.toString(), flag&0xFF, (byte) ((flag >>> 8)&0xF));
		} else g = null;

		if (c == '<') {
			childrenLoop:
			while (true) {
				if (pos >= s.length()) fail("未结束的类型列表",pos,s);
				c = s.charAt(pos);
				switch (c) {
					case '>':
						if (g.typeParameters.isEmpty()) fail("空类型列表", pos, s);
						pos++;
						break childrenLoop;
					case '*':
						pos++;
						g.addChild(Any.any);
						break;
					default:
						int ex;
						switch (c) {
							case '+' -> {
								pos++;
								ex = ParameterizedType.EXTENDS_WILDCARD << 8;
							}
							case '-' -> {
								pos++;
								ex = ParameterizedType.SUPER_WILDCARD << 8;
							}
							default -> ex = 0;
						}
						pOffset.value = pos;
						g.addChild(parseValue(s, pOffset, F_PRIMITIVE | ex, tmp, ctx));
						pos = pOffset.value;
						break;
				}
			}
			c = s.charAt(pos);
			pOffset.value = pos+1;
		}

		if (c == '.') {
			if (g == null) g = new ParameterizedType(tmp.toString(), flag&0xFF, (byte) ((flag >>> 8)&0xF));

			g.sub = (GenericSub) parseType(s, pOffset, tmp, F_SUBCLASS, ctx);
		} else if (c != ';') fail("未预料的 '"+c+"' 期待';'或'.'",pos,s);

		if (g != null) return g;
		return Type.klass(tmp.toString(), flag & 0xFF);
	}
	private static char parseTypeParam(CharSequence s, IntValue mi, CharList tmp) {
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

	@Override
	public @Nullable TypeVariableDeclaration resolveTypeVariable(String name) {return typeVariables.get(name);}

	@Deprecated(forRemoval = true)
	public @NotNull Map<String, TypeVariableDeclaration> getTypeVariables() {return Collections.emptyMap();}

	private static final class Itf implements IType {
		static final Itf itf = new Itf();

		private Itf() {}

		@Override public byte kind() {return OBJECT_BOUND;}
		@Override public void toDesc(CharList sb) {}
		@Override public void toString(CharList sb) {}
		@Override public String owner() { return "java/lang/Object"; }
		@Override
		public void validate(int positionType, int index) {
			if (positionType != E_TYPE_VARIABLE || index != 0) throw new IllegalStateException(this+"类型只能位于类型参数的第一项");
		}
		@Override public IType clone() { return itf; }
		@Override public String toString() {return "<接口占位符>";}
	}

	private static final class Any implements IType {
		static final Any any = new Any();

		private Any() {}

		@Override public byte kind() {return UNBOUNDED_WILDCARD;}
		@Override public void toDesc(CharList sb) {sb.append('*');}
		@Override public void toString(CharList sb) {sb.append('?');}
		@Override
		public void validate(int positionType, int index) {
			if (positionType != E_PARAMETERIZED) throw new IllegalStateException("<任意>只能在泛型中使用");
		}
		@Override public IType clone() {return any;}
		@Override public String toString() { return "?"; }
	}

	public static final class TVDSet extends LinkedOpenHashKVSet<String, TypeVariableDeclaration> {
		public TVDSet() {this(2);}
		public TVDSet(int initialCapacity) {super(initialCapacity, new ArrayList<>(initialCapacity));}
		public TVDSet(LinkedOpenHashKVSet<String, TypeVariableDeclaration> typeVariables) {super(typeVariables);}

		@Override protected String getKey(TypeVariableDeclaration value) {return value.name;}
	}
}