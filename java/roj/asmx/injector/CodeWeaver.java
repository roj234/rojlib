package roj.asmx.injector;

import org.jetbrains.annotations.NotNull;
import roj.asm.*;
import roj.asm.annotation.AList;
import roj.asm.annotation.Annotation;
import roj.asm.attr.*;
import roj.asm.cp.*;
import roj.asm.frame.Frame;
import roj.asm.frame.Var2;
import roj.asm.insn.*;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asmx.Context;
import roj.asmx.Transformer;
import roj.collect.ArrayList;
import roj.collect.BitSet;
import roj.collect.HashMap;
import roj.collect.HashSet;
import roj.collect.*;
import roj.config.data.CInt;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.ArrayUtil;
import roj.util.Helpers;

import java.util.*;

import static roj.asm.Opcodes.*;
import static roj.reflect.Debug.CLASS_DUMP;
import static roj.reflect.Debug.dump;

/**
 * @author Roj234
 * @since 2023/10/9 18:49
 */
public class CodeWeaver implements Transformer {
	public static final String PACKAGE = "roj/asmx/injector/";
	public static final String A_INJECTION = PACKAGE+"Weave";
	public static final String A_INJECT_POINT = PACKAGE+"Inject";
	public static final String A_SHADOW = PACKAGE+"Shadow";
	public static final String A_COPY = PACKAGE+"Copy";
	public static final String A_DYNAMIC = PACKAGE+"Dynamic";
	public static final String A_UNIQUE = PACKAGE+"Unique";
	public static final String A_FINAL = PACKAGE+"Final";
	public static final String A_REDIRECT = PACKAGE+"Redirect";
	public static final String A_OVERWRITE = PACKAGE+"OverwriteConstant";
	public static final String A_PATTERN_MATCH = PACKAGE+"PatternMatch";

	// region 特殊方法
	private static final String
	/**
	 * 在{@link PatternMatch}中指代任意【方法返回类型】的变量
	 * 当变量定义位置过早时，用于通用类型占位
	 */
	MARKER_AnyVariable = "$$$VALUE",
	/**
	 * 在TAIL注入模式中访问方法返回值
	 * @implNote 发现你未使用时会自动pop，不会一直放在栈上，但是如果你使用了返回值，那么尽早赋值，否则如果越过了try-catch代码块会导致代码生成异常
	 * @see Inject.At#TAIL
	 */
	MARKER_ReturnValue = "$$$VALUE",
	/**
	 * 在HEAD注入模式中标记后续紧跟的return无效
	 * 立即继续执行后续代码（非实际返回）
	 * @implNote 调用后会继续执行原方法逻辑（类似循环中的break）
	 * @see Inject.At#HEAD
	 */
	MARKER_Continue = "$$$VALUE",
	/**
	 * 构造器注入中的super()等效调用
	 * @see Inject.At#SUPER
	 */
	MARKER_InitSuper = "$$$CONSTRUCTOR",
	/**
	 * 构造器注入中的this()等效调用
	 * @see Inject.At#SUPER
	 */
	MARKER_InitThis = "$$$CONSTRUCTOR_THIS";

	/**
	 * 标记字节码匹配区域起始点
	 * @apiNote 与{@link #$$$MATCH_END()}配合划定代码匹配范围
	 * @see PatternMatch#matcher()
	 */
	public static void $$$MATCH_BEGIN() {}

	/**
	 * 标记字节码匹配区域终止点
	 * @apiNote 与{@link #$$$MATCH_BEGIN()}配合划定代码匹配范围
	 * @see PatternMatch#matcher()
	 */
	public static void $$$MATCH_END() {}

	/**
	 *   <table border="1" cellspacing="2" cellpadding="2">
	 *     <tr>
	 *       <th colspan="9"><span style="font-weight:normal">
	 *        这些以{@link #MARKER_ReturnValue}开头的方法()在Inject注解的函数中拥有特殊意义 <br>
	 *        如果返回值为某个具体的类，你也可以仿照开头在任何类中编写前缀为此的<i>静态</i>方法
	 *     </tr>
	 *     <tr>
	 *       <th>{@link Inject.At}</th>
	 *       <th><center>意义</center></th>
	 *       <th><center>示例</center></th>
	 *     </tr>
	 *     <tr>
	 *       <td>{@link Inject.At#HEAD}</td>
	 *       <td>立即(而不是结束)执行其后代码。相当于把注入函数作为循环体时的break语句。void返回值仅适用于该模式</td>
	 *       <td>return $$$VALUE_I()</td>
	 *     </tr>
	 *     <tr>
	 *       <td>{@link PatternMatch}</td>
	 *       <td>在匹配函数中指代任意同类型变量(当变量定义太早)。param第三项为true时,不同方法名称可以指代不同变量</td>
	 *       <td>new ArrayList<>($$$VALUE_I())</td>
	 *     </tr>
	 *     <tr>
	 *       <td>{@link Inject.At#TAIL}</td>
	 *       <td>指代函数返回值</td>
	 *       <td>int prevRet = $$$VALUE_I()</td>
	 *     </tr>
	 * <table>
	 */
	public static int $$$VALUE_I() {return 0;}
	public static long $$$VALUE_J() {return 0;}
	public static double $$$VALUE_D() {return 0;}
	public static float $$$VALUE_F() {return 0;}
	public static byte $$$VALUE_B() {return 0;}
	public static char $$$VALUE_C() {return 0;}
	public static short $$$VALUE_S() {return 0;}
	public static boolean $$$VALUE_Z() {return false;}
	public static void $$$VALUE_V() {}
	// endregion

	public static final class Patch {
		Patch next;

		public Patch(String self) {this.self = self;}

		public final String self;
		public String target;
		public List<String> newInterfaces = Collections.emptyList();

		Map<Pcd, IJPoint> inject = Collections.emptyMap();
		final Map<Pcd, IJPoint> inject() { if (inject.isEmpty()) inject = new HashMap<>(); return inject; }

		MethodNode copyClinit;
		Set<MemberNode> copied = Collections.emptySet();
		final Set<MemberNode> copied() { if (copied.isEmpty()) copied = new HashSet<>(Hasher.identity()); return copied; }

		// Shadow
		final HashSet<Pcd> preconditions = new HashSet<>();

		// lambda
		Map<BootstrapMethods.Item, List<MemberDescriptor>> lambda = Collections.emptyMap();

		public String toString() { return "Patch{'"+self+"' => '"+target+"'}"; }
	}
	private static final int SKIP_STATIC_CHECK = 16384;
	private static final class IJPoint {
		// 注解的信息
		String at;
		int flags;

		// replace模式: 方法原名, 检测 $$$CONSTRUCTOR调用
		String mapName;

		MethodNode method;

		int initBci = -1;
		List<?> nodeList;

		// Head
		@SuppressWarnings("unchecked")
		public List<Label> headJump() { return (List<Label>) nodeList; }

		// Pattern
		InsnList matcher;

		// Tail
		ToIntMap<InsnNode> assignId;
		@SuppressWarnings("unchecked")
		public List<InsnNode> retVal() { return (List<InsnNode>) nodeList; }

		Annotation extra;

		IJPoint next;

		IJPoint(MethodNode method, Annotation map) {
			this.method = method;

			flags = map.getInt("flags", 0);

			boolean isAbstract = method.getRawAttribute("Code") == null;
			switch (map.type()) {
				case A_OVERWRITE -> {
					at = "LDC";
					extra = map;
				}
				case A_REDIRECT -> {
					at = "INVOKE";
					extra = map;
				}
				case A_PATTERN_MATCH -> {
					at = "PATTERN";
					extra = map;
				}
				default -> {
					at = map.getEnumValue("at", "SUPER");
					switch (at) {
						// might replace to native method(FastJNI)
						case "REPLACE", "REMOVE":
							isAbstract = false;
							break;
						case "HEAD", "TAIL":
							nodeList = new java.util.ArrayList<>();
							break;
						default:
							break;
					}
				}
			}

			if (isAbstract) throw new IllegalArgumentException("方法不能是抽象的 "+method);
			mapName = map.getString("value", method.name());
		}

		public BitSet getOccurrences() {
			int[] a = extra.getIntArray("occurrences");
			return a == null || a.length == 0 ? null : BitSet.from(a);
		}
	}

	protected final Map<String, Patch> registry = new HashMap<>();

	public final void load(ClassNode data) throws WeaveException {
		Patch nx = read(data);
		if (nx == null) return;
		if (registry.putIfAbsent(nx.self, nx) != null) {
			throw new WeaveException("注入类"+nx.self+"已存在于当前实例的注册表！");
		}
		nx.next = registry.put(nx.target, nx);
	}
	public final boolean unloadBySource(String source) {
		Patch self = registry.remove(source);
		if (self == null) return false;

		Patch nx = registry.get(self.target), prev = null;
		while (nx != null) {
			if (nx.self.equals(source)) {
				if (prev == null) registry.remove(self.target);
				else prev.next = nx.next;
				return true;
			}
			prev = nx;
			nx = nx.next;
		}
		return false;
	}
	public final boolean unloadByTarget(String target) { return registry.remove(target) != null; }

	private static AbstractMap<String, String> getFakeMap(Patch nx, Map<String, Patch> ctx) {
		return new AbstractMap<>() {
			@Override
			public String get(Object key) {
				if (nx != null && key.equals(nx.self)) return nx.target;

				Patch nx = ctx.get(key);
				return nx == null ? null : nx.target;
			}

			@NotNull
			@Override
			public Set<Entry<String, String>> entrySet() {return Collections.emptySet();}
		};
	}

	public Patch read(ClassNode data) throws WeaveException {
		if (CLASS_DUMP) dump("patch", data);

		Patch nx = new Patch(data.name());

		Map<String, Annotation> annotation = getAnnotations(data,data);
		Annotation a = annotation.get(A_INJECTION);
		if (a == null || (!a.containsKey("value") && !a.containsKey("target")))
			throw new WeaveException(data.name()+"不是有效的注入类: 缺少注解或注解缺少参数");

		nx.target = internalName(a.containsKey("target") ? a.getClass("target").owner : a.getString("value"));
		if (nx.target.equals("/")) nx.target = data.parent();

		if (a.getBool("copyInterfaces", true)) nx.newInterfaces = ArrayUtil.immutableCopyOf(data.interfaces());

		//int flag = a.getInt("flags");

		a = annotation.get(A_DYNAMIC);
		if (a != null) {
			if (!shouldApply(A_INJECTION, data, a.getList("value"))) {
				return null;
			}
		}

		// 检测特殊方法, 删除桥接方法
		ArrayList<MethodNode> autoCopy = new ArrayList<>();
		ArrayList<MethodNode> methods = data.methods;
		for (int i = methods.size() - 1; i >= 0; i--) {
			MethodNode method = methods.get(i);
			String name = method.name();
			if (name.startsWith("$$$")) {
				if (method.getRawAttribute(Attribute.ClAnnotations.name) != null)
					throw new WeaveException("特殊方法("+name+")不能包含注解");

				if (!name.startsWith(MARKER_InitSuper)) {
					if (0 == (method.modifier & ACC_STATIC)) throw new WeaveException("特殊方法("+name+")必须静态");
					if (!method.rawDesc().startsWith("()")) throw new WeaveException("特殊方法("+name+")不能有参数");
				} else if (!method.rawDesc().endsWith(")V")) {
					throw new WeaveException("构造器标记("+name+")必须返回void");
				} else if (0 != (method.modifier & ACC_STATIC)) throw new WeaveException("构造器标记("+name+")不能静态");
			}

			if (0 != (method.modifier & ACC_SYNTHETIC))
				autoCopy.add(methods.get(i));
		}

		Map<String, Patch> ctx = registry();
		AbstractMap<String, String> fakeMap = getFakeMap(nx, ctx);
		for (MethodNode method : data.methods) {
			String desc = ClassUtil.getInstance().mapMethodParam(fakeMap, method.rawDesc());
			method.rawDesc(desc);
		}
		for (FieldNode field : data.fields) {
			String desc = ClassUtil.getInstance().mapFieldType(fakeMap, field.rawDesc());
			if (desc != null) field.rawDesc(desc);
		}

		// 处理 Final Copy Shadow Unique
		readAnnotations(data, nx, data.methods);
		readAnnotations(data, nx, data.fields);

		mapReference(data.cp, nx, ctx);

		// 查找无法访问的方法
		HashSet<MemberDescriptor> inaccessible = new HashSet<>();
		boolean isSamePackage = ClassUtil.arePackagesSame(data.name(), nx.target);
		findInaccessible(data, data.methods, inaccessible, isSamePackage);
		findInaccessible(data, data.fields, inaccessible, isSamePackage);

		// 复制走clinit
		int clInit = data.getMethod("<clinit>", "()V");
		if (clInit >= 0) {
			MethodNode mn = data.methods.remove(clInit);
			nx.copied().add(mn);
			nx.copyClinit = mn;
		}

		List<BootstrapMethods.Item> lambdaPending = new ArrayList<>();

		// 检测无法访问的方法(nixim中private等), 循环添加autoCopy中的方法, 并复制用到的lambda
		Validator cv = new Validator(data, inaccessible, nx, autoCopy);
		for (IJPoint state : nx.inject.values()) {
			do {
				cv.state = state;
				cv.visit(state.method);

				Code code = (Code) state.method.parsed(data.cp).getRawAttribute("Code");
				if (code != null) copyLambdas(data, code, nx, lambdaPending);

				if (state.initBci == 0 && state.mapName.equals("<init>"))
					throw new WeaveException("没有找到 "+MARKER_InitSuper+"或"+MARKER_InitThis+" in "+state.method);

				try {
					parseInject(state, data);
				} catch (WeaveException e) {
					throw new WeaveException("处理方法"+data.name()+"."+state.method.name()+"时出现了错误: ", e);
				}

				state = state.next;
			} while (state != null);
		}
		cv.state = null;

		for (MemberNode node : nx.copied) {
			if (node instanceof MethodNode mn)
				cv.copied.add(mn);
		}

		boolean hasNew;
		do {
			for (BootstrapMethods.Item item : lambdaPending) {
				List<Constant> args = item.arguments;
				find:
				for (int i = 0; i < args.size(); i++) {
					Constant c = args.get(i);
					if (c.type() != Constant.METHOD_HANDLE) continue;

					CstRef ref = ((CstMethodHandle) c).getRef();
					if (!ref.owner().equals(data.name())) break;

					List<MethodNode> nodes = data.methods;
					for (int j = 0; j < nodes.size(); j++) {
						MethodNode mn = nodes.get(j);
						if (mn.matches(ref.nameAndType())) {
							Pcd pcd = new Pcd();
							pcd.name = mn.name();
							pcd.desc = mn.rawDesc();
							pcd.mapOwner = nx.target;
							pcd.mapName = "nx^lambda@"+randomId((System.nanoTime() << 32) | mn.name().hashCode());
							nx.preconditions.add(pcd);

							ref.clazz().setValue(data.cp.getUtf(nx.target));
							// name changed in hasNewCopyMethod
							ref.nameAndType(data.cp.getDesc(pcd.mapName, mn.rawDesc()));

							// noinspection all
							nodes.remove(j);

							// 循环添加。
							cv.copied.add(mn);
							break find;
						}
					}

					throw new WeaveException("无法找到符合条件的 lambda 方法: "+ref.nameAndType());
				}
			}
			lambdaPending.clear();

			hasNew = false;
			for (MethodNode node : cv.copied) {
				Attribute cc = node.getRawAttribute("Code");
				if (cc == null || cc.getClass() != UnparsedAttribute.class) continue;

				cv.visit(node);
				nx.copied().add(node);
				hasNew = true;

				Code code = (Code) node.parsed(data.cp).getRawAttribute("Code");
				if (code != null) copyLambdas(data, code, nx, lambdaPending);
			}
		} while (hasNew);

		boolean hasNewCopyMethod = false;
		Pcd tmpPcd = new Pcd();
		Map<String, String> map = Collections.singletonMap(nx.self, nx.target);
		for (MemberNode node : nx.copied) {
			String desc = node.rawDesc();
			if (desc.startsWith("(")) // 不处理字段
				node.rawDesc(ClassUtil.getInstance().mapMethodParam(map, desc));

			tmpPcd.name = node.name();
			if (tmpPcd.name.startsWith("<")) continue;

			tmpPcd.desc = desc;
			Pcd pcd = nx.preconditions.intern(tmpPcd);
			if (pcd == tmpPcd) {
				// manual rename
				pcd.mapOwner = nx.target;
				pcd.mapName = "nx^copyRef@@"+randomId((System.nanoTime() << 32) | node.name().hashCode());
				tmpPcd = new Pcd();
				hasNewCopyMethod = true;
			}
			node.name(pcd.mapName);
		}

		if (hasNewCopyMethod)
			copyIndirectUsages(tmpPcd, nx, nx.copied);

		List<MemberNode> list = new ArrayList<>(nx.inject.values().size());
		for (IJPoint t : nx.inject.values()) list.add(t.method);
		copyIndirectUsages(tmpPcd, nx, list);

		// special case
		nx.copied.remove(nx.copyClinit);

		// fields
		for (MemberNode node : nx.copied) node.parsed(data.cp);
		return nx;
	}
	// region 补丁解析相关函数
	private static String internalName(String className) { return className.replace('.', '/'); }

	private static Map<String, Annotation> getAnnotations(Attributed node, ClassNode data) {
		Annotations attr = node.getAttribute(data.cp, Attribute.ClAnnotations);
		if (attr == null || attr.writeIgnore()) return Collections.emptyMap();
		HashMap<String, Annotation> map = new HashMap<>(attr.annotations.size());
		List<Annotation> annotations = attr.annotations;
		for (int i = annotations.size()-1; i >= 0; i--) {
			Annotation a = annotations.get(i);
			map.put(a.type(), a);
			if (a.type().startsWith("roj/asmx/injector/")) annotations.remove(i);
		}
		return map;
	}
	private void readAnnotations(ClassNode data, Patch patch, ArrayList<? extends MemberNode> nodes) throws WeaveException {
		Annotation a;
		Map<String, Annotation> map;

		for (int i = nodes.size()-1; i >= 0; i--) {
			MemberNode node = nodes.get(i);
			if (node.name().startsWith("$$$")) {
				nodes.remove(i);
				continue;
			}

			Pcd pcd = null;

			map = getAnnotations(node, data);
			Annotation dyn = map.get(A_DYNAMIC);

			a = map.get(A_SHADOW);
			if (a != null) {
				if (verifyAnnotations(data, a, map, node, null, dyn)) continue;

				pcd = new Pcd();
				pcd.name = node.name();
				pcd.desc = node.rawDesc();

				pcd.mapName = a.getString("value", pcd.name);
				if (pcd.mapName.equals("/")) pcd.mapName = pcd.name;
				pcd.mapOwner = internalName(a.getString("owner", patch.target));

				pcd.mode = Pcd.SHADOW | node.modifier;

				patch.preconditions.add(pcd);

				if ((a.getInt("flags") & Inject.RUNTIME_MAP) != 0)
					pcd.mapName = mapName(data.name(), pcd.mapOwner, pcd.mapName, node);

				a = map.get(A_FINAL);
				if (a != null) {
					pcd.mode |= ACC_FINAL;
					if (a.containsKey("setFinal")) {
						if (a.getBool("setFinal")) throw new WeaveException("Shadow不能配Final(setFinal=true): "+data.name()+'.'+node.name()+": "+map.values());
						pcd.mode |= Pcd.REAL_DEL_FINAL;
					}
				}

				nodes.remove(i);
			}

			a = map.get(A_COPY);
			if (a != null) {
				if (verifyAnnotations(data, a, map, node, pcd, dyn)) continue;

				pcd = new Pcd();
				pcd.name = node.name();
				pcd.desc = node.rawDesc();

				pcd.mapName = a.getString("value", pcd.name);
				pcd.mapOwner = patch.target;

				pcd.mode = Pcd.COPY | node.modifier;

				patch.preconditions.add(pcd);

				if ((a.getInt("flags") & Inject.RUNTIME_MAP) != 0)
					pcd.mapName = mapName(data.name(), pcd.mapOwner, pcd.mapName, node);

				boolean method = pcd.name.startsWith("(");

				a = map.get(A_UNIQUE);
				if (a != null) {
					pcd.mapName = "patch^copy@"+randomId(System.nanoTime() << 32 | node.name().hashCode());
				}

				a = map.get(A_FINAL);
				if (a != null) {
					if (method) throw new WeaveException("Copy&Final的组合不能用在方法上: "+data.name()+'.'+node.name()+": "+map.values());
					pcd.mode |= ACC_FINAL;
					if (a.containsKey("setFinal")) {
						pcd.mode |= a.getBool("setFinal") ? Pcd.REAL_ADD_FINAL : Pcd.REAL_DEL_FINAL;
					}
				}

				patch.copied().add(node);
				nodes.remove(i);
			}

			a = map.get(A_INJECT_POINT);
			if (a == null) a = map.get(A_REDIRECT);
			if (a == null) a = map.get(A_OVERWRITE);
			if (a == null) a = map.get(A_PATTERN_MATCH);
			if (a != null) {
				if (verifyAnnotations(data, a, map, node, pcd, dyn)) continue;

				pcd = new Pcd();
				pcd.name = node.name();
				pcd.desc = a.getString("injectDesc", node.rawDesc());

				pcd.mapName = a.getString("value", pcd.name);
				pcd.mapOwner = patch.target;

				pcd.mode = Pcd.INJECT | node.modifier;

				if ((a.getInt("flags") & Inject.RUNTIME_MAP) != 0)
					pcd.mapName = mapName(data.name(), pcd.mapOwner, pcd.mapName, node);

				pcd.name = pcd.mapName;
				IJPoint state = new IJPoint((MethodNode) node, a);
				IJPoint prev = patch.inject().put(pcd, state);
				if (prev != null) state.next = prev;
				nodes.remove(i);

				if (a.type().equals(A_REDIRECT)) {
					patch.copied().add(node);
					state.flags |= SKIP_STATIC_CHECK;
				}
			}
		}
	}
	private boolean verifyAnnotations(ClassNode data, Annotation annotation, Map<String, Annotation> annotations, MemberNode node, Pcd pcd, Annotation dyn) throws WeaveException {
		if (pcd != null) throw new WeaveException("冲突的注解: "+data.name()+'.'+node+": "+annotations.values());
		if (dyn != null) {
			if (!shouldApply(annotation.type(), data, dyn.getList("value"))) return true;
		}
		if (node.name().equals("<init>"))
			throw new WeaveException("不支持操作: 自3.0起,你不能在构造器中直接注入: "+data.name()+'.'+node+"\n"+
					"替代方案: 使用非静态方法，Inject到<init>，并调用"+MARKER_InitSuper+"或"+MARKER_InitThis+"\n"+
					"为何删除: 1. 容易造成误解，2.无法使用this(...)");
		return false;
	}

	private static class Validator extends CodeVisitor {
		private final ClassNode data;
		private final HashSet<MemberDescriptor> inaccessible;
		private final Patch nx;
		private final ArrayList<MethodNode> autoCopy;

		private final MemberDescriptor tmp = ClassUtil.getInstance().sharedDesc;
		private final Pcd tmp2 = new Pcd();
		private int initFlag;
		private boolean isConstructor;

		IJPoint state;
		HashSet<MethodNode> copied = new HashSet<>(Hasher.identity());

		private MethodNode mn;

		public Validator(ClassNode data, HashSet<MemberDescriptor> inaccessible, Patch nx, ArrayList<MethodNode> autoCopy) {
			this.data = data;
			this.inaccessible = inaccessible;
			this.nx = nx;
			this.autoCopy = autoCopy;
		}

		public void visit(MethodNode node) {
			this.mn = node;
			if (node.name().equals("<init>")) {
				initFlag = 1;
			} else if (node.name().equals("<clinit>")) {
				initFlag = 2;
			} else {
				initFlag = 0;
			}
			isConstructor = false;

			Attribute code = node.getRawAttribute("Code");
			if (code != null) visit(data.cp, AsmCache.reader(code));
		}

		@Override
		public void invoke(byte code, CstRef method) {
			checkAccess(method);

			String name = method.name();
			if (name.startsWith(MARKER_InitSuper)) {
				if (isConstructor) throw new IllegalStateException("构造器("+MARKER_InitSuper+")只能调用一次");
				isConstructor = true;
				if (state != null) {
					state.initBci = bci;
				}
			}

			if (state != null && (code == INVOKESTATIC || code == INVOKESPECIAL)) {
				if (name.equals(state.mapName) && method.rawDesc().equals(state.method.rawDesc())) {
					state.flags |= 65536;
				}
			}
		}

		@Override
		public void field(byte code, CstRef field) {
			checkAccess(field);

			if (initFlag != 2) {
				if (Opcodes.toString(code).startsWith("Get")) return;

				tmp2.name = field.name();
				tmp2.desc = field.rawDesc();
				Pcd pcd = nx.preconditions.find(tmp2);
				if (pcd != tmp2) {
					if ((pcd.mode&ACC_STATIC) == 0 && initFlag == 1) return;
					if ((pcd.mode&ACC_FINAL) != 0) {
						Helpers.athrow(new WeaveException("不能修改final或为实际上final的"+field+"\n"+
								"源: "+data.name()+"\n"+
								"函数: "+mn));
					}
				}
			}
		}

		private void checkAccess(CstRef ref) {
			if (ref.owner().equals(data.name()) && inaccessible.contains(tmp.read(ref))) {
				if (!autoCopy(tmp))
					Helpers.athrow(new WeaveException("请使用@Copy注解以在运行时访问"+tmp));
			}
		}

		private boolean autoCopy(MemberDescriptor d) {
			for (int i = 0; i < autoCopy.size(); i++) {
				MethodNode node = autoCopy.get(i);
				if (node.matches(d)) {
					copied.add(node);
					return true;
				}
			}
			return false;
		}
	}

	private static void findInaccessible(ClassNode data, ArrayList<? extends MemberNode> nodes, HashSet<MemberDescriptor> inaccessible, boolean isSamePackage) {
		for (int i = 0; i < nodes.size(); i++) {
			MemberNode remain = nodes.get(i);
			int acc = remain.modifier;
			if (((acc & (ACC_PRIVATE | ACC_STATIC)) != ACC_STATIC) || ((acc & ACC_PUBLIC) == 0 && !isSamePackage)) {
				inaccessible.add(new MemberDescriptor(data.name(), remain.name(), remain.rawDesc()));
			}
		}
	}
	private static void parseInject(IJPoint s, ClassNode data) throws WeaveException {
		MethodNode method = s.method;
		Code code = method.getAttribute(data.cp, Attribute.Code);
		if (s.initBci > 0) {
			LineNumberTable ln = (LineNumberTable) code.getRawAttribute("LineNumberTable");
			if (ln != null) {
				for (InsnNode node : code.instructions) {
					if (node.bci() > s.initBci) break;

					List<LineNumberTable.Item> list = ln.list;
					for (int i = list.size()-1; i >= 0; i--) {
						LineNumberTable.Item item = list.get(i);
						if (item.pos.equals(node.pos())) {
							list.remove(i);
						}
					}
				}
			}
		}

		switch (s.at) {
			case "REMOVE", "REPLACE": break;
			case "HEAD": {
				List<Label> usedContinue = s.headJump();
				int paramLength = TypeHelper.paramSize(method.rawDesc());
				boolean changedParam = false;

				// 2.0不再限制对入参的更改
				for (InsnNode node : code.instructions) {
					int id = node.getVarId();
					if (id >= 0) {
						if (node.opName().startsWith("Store", 1)) {
							if (id >= paramLength) continue;

							changedParam = true;
						}
					} else if (node.opName().endsWith("Return")) {
						InsnNode node1 = node.prev();
						if (node1.opcode() == INVOKESTATIC && node1.desc().name.startsWith(MARKER_Continue)) {
							if (!Type.methodDescReturn(node1.desc().rawDesc).equals(method.returnType()))
								throw new WeaveException("返回值指代#"+s.retVal().size()+"(bci: "+node1.bci()+")的返回值不适用于目的方法的"+method.returnType());

							usedContinue.add(code.instructions.labelAt(node1.pos()));
							usedContinue.add(code.instructions.labelAt(node.end()));
						}
					}
				}

				if (usedContinue.isEmpty() && !changedParam) throw new WeaveException("Head注入未用到CONTINUE, 你应该使用REPLACE模式");
			}
			break;
			case "INVOKE":
				// nothing to check
				break;
			case "LDC":
				validateMatchType(s, s.extra.getString("matchValue"));
				String value = s.extra.getString("replaceValue", "");
				if (!value.isEmpty()) validateMatchType(s, value);
				break;
			case "TAIL": {
				ToIntMap<InsnNode> usedVar = new ToIntMap<>();
				int paramLength = TypeHelper.paramSize(method.rawDesc());

				for (InsnNode node : code.instructions) {
					int id = node.getVarId();
					if (id >= 0) {
						int bci = node.bci();
						if (node.opName().startsWith("Load", 1)) {
							if (id >= paramLength) continue;

							usedVar.putInt(node.unshared(), id);
						} else if (bci > 0) {
							node = node.prev();
							if (node.opcode() == INVOKESTATIC && node.desc().name.startsWith(MARKER_ReturnValue)) {
								if (!Type.methodDescReturn(node.desc().rawDesc).equals(method.returnType()))
									throw new WeaveException("返回值指代#"+s.retVal().size()+"(bci: "+node.bci()+")的返回值不适用于目的方法的"+method.returnType());
								s.retVal().add(node.unshared());
							}
						}
					}
				}

				// 备份参数
				if (!usedVar.isEmpty()) s.assignId = usedVar;
			}
			break;
			// rename并注入super
			case "SUPER":
				if ((s.flags & 65536) == 0) {
					s.at = "REPLACE";
				}
				break;
			case "PATTERN":
				String matcher = s.extra.getString("matcher");
				MethodNode mn = data.getMethodObj(matcher);
				if (mn == null) throw new WeaveException("未找到matcher:"+matcher);
				// search
				s.matcher = getPattern(data, mn, null);
				// replace
				s.method.getAttribute(data.cp, Attribute.Code).instructions = getPattern(data, s.method, s);
				break;
		}
	}
	private static void copyLambdas(ClassNode data, Code code, Patch nx, List<BootstrapMethods.Item> pending) throws WeaveException {
		for (Map.Entry<Label, Object> entry : code.instructions.nodeData()) {
			Object o = entry.getValue();
			if (o.getClass() == MemberDescriptor.class) {
				MemberDescriptor desc = (MemberDescriptor) o;
				if (desc.owner == null) {
					BootstrapMethods bsm = data.getAttribute(data.cp, Attribute.BootstrapMethods);
					if (bsm == null) throw new WeaveException(data.name()+"存在错误,BootstrapMethods不存在");

					BootstrapMethods.Item key = bsm.methods.get(desc.modifier);
					// NiximClass不应有实例。 (static method也不建议使用)
					desc.rawDesc = ClassUtil.getInstance().mapMethodParam(Collections.singletonMap(data.name(), nx.target), desc.rawDesc);

					if (nx.lambda.isEmpty()) nx.lambda = new HashMap<>();
					List<MemberDescriptor> list = nx.lambda.computeIfAbsent(key, Helpers.fnArrayList());
					if (list.isEmpty()) pending.add(key);
					list.add(desc);
				}
			}
		}
	}
	private static void copyIndirectUsages(Pcd tmp, Patch patch, Collection<MemberNode> nodes) {
		for (MemberNode node : nodes) {
			Code code = node.getAttribute(null, Attribute.Code);
			if (code == null) continue;

			for (Map.Entry<Label, Object> entry : code.instructions.nodeData()) {
				if (entry.getValue().getClass() == MemberDescriptor.class) {
					MemberDescriptor desc = (MemberDescriptor) entry.getValue();
					if (desc.name.equals(patch.self)) {
						tmp.name = desc.name;
						tmp.desc = desc.rawDesc;
						Pcd pcd = patch.preconditions.find(tmp);
						if (pcd != tmp && pcd.mapName != null) {
							desc.owner = pcd.mapOwner;
							desc.name = pcd.mapName;
						}
					}
				}
			}

			List<Frame> frames = code.frames;
			if (frames != null) {
				for (Frame frame : frames) {
					for (Var2 v : frame.stacks)
						if (patch.self.equals(v.owner))
							v.owner = patch.target;
					for (Var2 v : frame.locals)
						if (patch.self.equals(v.owner))
							v.owner = patch.target;
				}
			}
			// TODO set fv flag?
		}
	}
	@SuppressWarnings("ResultOfMethodCallIgnored")
	private static void validateMatchType(IJPoint s, String data1) throws WeaveException {
		switch (s.extra.getInt("matchType", 0)) {
			case Constant.INT: Integer.parseInt(data1); break;
			case Constant.LONG: Long.parseLong(data1); break;
			case Constant.DOUBLE: Double.parseDouble(data1); break;
			case Constant.FLOAT: Float.parseFloat(data1); break;
			case Constant.CLASS: Type.fieldDesc(data1); break;
			case Constant.STRING: break;
			default: throw new WeaveException("OverwriteConstant的matchType必须是Constant中的有效类别ID");
		}
	}
	private static InsnList getPattern(ClassNode data, MethodNode method, IJPoint state) throws WeaveException {
		Code code = method.getAttribute(data.cp, Attribute.Code);
		if (code == null) throw new WeaveException("方法"+method.name()+"不能是抽象的");

		Label start = null, end = null;
		label:
		for (InsnNode node : code.instructions) {
			MemberDescriptor desc = node.descOrNull();
			if (desc != null) {
				switch (desc.name) {
					case "$$$MATCH_BEGIN":
						if (start != null) throw new WeaveException("重复的MATCH_BEGIN");
						start = code.instructions.labelAt(node.end());
					break;
					case "$$$MATCH_END":
						end = code.instructions.labelAt(node.pos());
					break label;
					case MARKER_Continue:
						if (state == null) throw new WeaveException("Continue不能在这里使用");
						Label pos = code.instructions.labelAt(node.pos());
						state.headJump().add(pos);
						state.headJump().add(pos);
					break;
				}
			}
		}

		if (start == null || end == null) throw new WeaveException(method.name()+"不是有效的Match/Replace方法");
		return code.instructions.copySlice(start, end);
	}
	// endregion

	public static boolean mapReference(ConstantPool cp, Patch nx, Map<String, Patch> ctx) {
		boolean changed = false;
		AbstractMap<String, String> fakeMap = getFakeMap(nx, ctx);

		Pcd tmpPCD = new Pcd();
		List<Constant> constants = cp.data();
		for (int i = 0; i < constants.size(); i++) {
			Constant c = constants.get(i);
			switch (c.type()) {
				case Constant.METHOD:
				case Constant.INTERFACE:
				case Constant.FIELD:
					CstRef ref = (CstRef) c;
					Patch nx1 = nx!=null&&ref.owner().equals(nx.self) ? nx : ctx.get(ref.owner());
					if (nx1 != null && nx1.self.equals(ref.owner())) {
						tmpPCD.name = ref.name();
						tmpPCD.desc = ref.rawDesc();
						Pcd pcd = nx1.preconditions.find(tmpPCD);
						if (pcd != tmpPCD) {
							changed = true;

							if (!pcd.mapOwner.equals(ref.owner())) ref.clazz(cp.getClazz(pcd.mapOwner));
							if (!pcd.mapName.equals(pcd.name)) ref.nameAndType(cp.getDesc(pcd.mapName, ref.rawDesc()));

							CstUTF desc = ref.nameAndType().rawDesc();
							cp.setUTFValue(desc, ClassUtil.getInstance().mapMethodParam(fakeMap, desc.str()));
						}
					}
					break;
			}
		}
		for (int i = 0; i < constants.size(); i++) {
			Constant c = constants.get(i);
			if (c.type() == Constant.CLASS) {
				CstClass ref1 = (CstClass) c;
				String name = ref1.value().str();
				Patch patch = ctx.get(name);
				if (patch != null && patch.self.equals(name)) {
					changed = true;

					ref1.setValue(cp.getUtf(patch.target));
				}
			}
		}

		return changed;
	}

	@Override
	public boolean transform(String name, Context ctx) throws WeaveException {
		if (registry.isEmpty()) return false;

		Patch nx1 = registry.get(name);
		if (nx1 != null) {
			patch(ctx.getData(), nx1);
			return true;
		} else {
			return mapReference(ctx.getConstantPool(), null, registry);
		}
	}

	public static void patch(ClassNode data, Patch patches) throws WeaveException {
		if (!data.name().equals(patches.target)) throw new WeaveException("期待目标为"+patches.target+"而不是"+data.name());

		if (CLASS_DUMP) dump("patch_in", data);
		while (patches != null) {
			data.unparsed();
			weave(data, patches);
			patches = patches.next;
		}
		data.unparsed();
		if (CLASS_DUMP) dump("patch_out", data);
	}
	private static void weave(ClassNode data, Patch patch) throws WeaveException {
		// 添加接口
		List<String> itfs = patch.newInterfaces;
		for (int i = 0; i < itfs.size(); i++) data.addInterface(itfs.get(i));

		List<MethodNode> methods = data.methods;
		Pcd tmp = new Pcd();

		// region 检查 Shadow 兼容性
		if (!patch.preconditions.isEmpty()) {
			Pcd.INVERT_EQUALS.set(true);
			HashSet<Pcd> pcdRev = new HashSet<>(patch.preconditions);

			tmp.mapOwner = data.name();
			try {
				verifyCopyShadow(patch.self, pcdRev, tmp, data.fields);
				verifyCopyShadow(patch.self, pcdRev, tmp, methods);
			} finally {
				Pcd.INVERT_EQUALS.remove();
			}

			for (Pcd pcd : pcdRev) {
				if ((pcd.mode&Pcd.SHADOW) != 0 && pcd.mapOwner.equals(data.name())) {
					throw new WeaveException("@Shadow的目标缺失\n"+
						"源: "+patch.self+"\n"+
						"对象"+pcd+"\n"+
						"目标方法: "+data.methods+"\n"+
						"目标字段: "+data.fields);
				}
			}
		}
		// endregion
		// region 实施 Inject
		if (!patch.inject.isEmpty()) {
			for (int i = 0; i < methods.size(); i++) {
				MethodNode mn = methods.get(i);

				tmp.name = mn.name();
				tmp.desc = mn.rawDesc();

				IJPoint state = patch.inject.remove(tmp);
				if (state == null) continue;

				if ((state.flags&SKIP_STATIC_CHECK) == 0 && (state.method.modifier & ACC_STATIC) != (mn.modifier & ACC_STATIC)) {
					throw new WeaveException(patch.self+"."+state.method+"无法覆盖"+data.name()+"."+mn+": static不匹配");
				}

				while (state != null) {
					mn = doInject(state, data, mn);
					state = state.next;
				}

				methods.set(i, mn);
			}
			for (int i = methods.size()-1; i >= 0; i--) {
				MethodNode mn = methods.get(i);
				if (mn == null) methods.remove(i);
			}
			// region 检查存在性
			if (!patch.inject.isEmpty()) {
				outer:
				for (Iterator<IJPoint> itr = patch.inject.values().iterator(); itr.hasNext(); ) {
					IJPoint state = itr.next();
					while (state != null) {
						if ((state.flags & Inject.OPTIONAL) == 0) {
							continue outer;
						}
						state = state.next;
					}
					itr.remove();
				}

				if (!patch.inject.isEmpty())
					throw new WeaveException("以下Inject方法没有在目标找到, 源: "+patch.self+": "+patch.inject.keySet()+", 目标的方法: "+data.methods);
			}
			// endregion
		}
		// endregion


		if (patch.lambda != null && !patch.lambda.isEmpty()) {
			BootstrapMethods selfBSM = data.getAttribute(data.cp,Attribute.BootstrapMethods);
			if (selfBSM == null) data.addAttribute(selfBSM = new BootstrapMethods());

			for (Map.Entry<BootstrapMethods.Item, List<MemberDescriptor>> entry : patch.lambda.entrySet()) {
				int newId = selfBSM.methods.size();
				selfBSM.methods.add(entry.getKey());

				List<MemberDescriptor> info = entry.getValue();
				for (int i = 0; i < info.size(); i++) {
					info.get(i).modifier = (char) newId;
				}
			}
		}


		for (MemberNode node : patch.copied) {
			if (node.rawDesc().startsWith("(")) methods.add((MethodNode) node);
			else data.fields.add((FieldNode) node);
		}


		if (patch.copyClinit != null) {
			Code injectClInit = patch.copyClinit.getAttribute(null, Attribute.Code);

			Code code;
			int last;
			int clinit_id = data.getMethod("<clinit>");
			if (clinit_id >= 0) {
				code = data.methods.get(clinit_id).getAttribute(data.cp, Attribute.Code);
				InsnList insn = code.instructions;
				last = insn.getPcMap().last();
				insn.replaceRange(last, insn.bci(), injectClInit.instructions, true);
			} else {
				MethodNode mn = new MethodNode(ACC_PUBLIC | ACC_STATIC, data.name(), "<clinit>", "()V");
				mn.addAttribute(code = new Code(mn));
				methods.add(mn);
				last = 0;

				code.instructions = injectClInit.instructions.copy();
			}

			copyLineNumbers(injectClInit, code, last);
		}


		for (int i = 0; i < data.methods.size(); i++) {
			Code m = data.methods.get(i).getAttribute(null, Attribute.Code);
			if (m != null) removeLVT(m);
		}

		assert data.verify();
	}
	//region 应用补丁
	private static void verifyCopyShadow(String self, HashSet<Pcd> pcds, Pcd tmp, List<? extends MemberNode> nodes) throws WeaveException {
		int size = nodes.size();
		for (int i = 0; i < size; i++) {
			MemberNode node = nodes.get(i);
			tmp.mapName = node.name();
			tmp.desc = node.rawDesc();
			Pcd pcd = pcds.find(tmp);

			if (pcd != tmp) {
				pcds.remove(tmp);

				if ((pcd.mode & Pcd.REAL_ADD_FINAL) != 0) {
					node.modifier |= ACC_FINAL;
				} else if ((pcd.mode & Pcd.REAL_DEL_FINAL) != 0) {
					node.modifier &= ~ACC_FINAL;
				}

				// shadow did not process unique
				if ((pcd.mode & Pcd.SHADOW) != 0) {
					// 排除nixim有final, target没有的情况
					if ((node.modifier()& ACC_STATIC) != (pcd.mode& ACC_STATIC) ||
						(node.modifier()& ACC_FINAL) > (pcd.mode& ACC_FINAL)) {
						throw new WeaveException(self+'.'+pcd.name+" Shadow => "+pcd.mapOwner+'.'+pcd.mapName+" static/final不匹配");
					}
				} else if ((pcd.mode & Pcd.COPY) != 0) {
					throw new WeaveException(self+'.'+pcd.name+" Copy => "+pcd.mapOwner+'.'+pcd.mapName+" 已存在");
				}
			}
		}
	}
	private static MethodNode doInject(IJPoint s, ClassNode data, MethodNode input) throws WeaveException {
		Code nxCode = (Code) s.method.getRawAttribute("Code");
		s.method.__setOwner(data.name());

		if (s.mapName.equals("<init>") && !s.at.equals("TAIL")) {
			MemberDescriptor iin = nxCode.instructions.getNodeAt(s.initBci).desc();
			st:
			if (iin.name.startsWith(MARKER_InitThis)) {
				if (iin.rawDesc.equals(s.method.rawDesc())) throw new WeaveException("this()循环调用!");
				iin.owner = data.name();
				for (int i = 0; i < data.methods.size(); i++) {
					MethodNode mn = data.methods.get(i);
					if (mn.rawDesc().equals(iin.rawDesc)) break st;
				}
				throw new WeaveException("使用的this()构造器不存在");
			} else {
				// 无法检查上级构造器是否存在, 但是Object还是可以查一下
				iin.owner = data.parent();
				if (data.parent().equals("java/lang/Object") && !"()V".equals(iin.rawDesc)) {
					throw new WeaveException("覆盖的构造器中的上级调用不存在(Object parent)\n"+
						"你也许忘了: $$CONSTRUCTOR是【目标】中的super()");
				}
			}

			iin.name = "<init>";
		}

		Code mnCode = input.getAttribute(data.cp, Attribute.Code);
		switch (s.at) {
			case "REMOVE": return null;
			case "REPLACE": default:
				s.method.name(s.mapName);
				return s.method;
			case "HEAD": {
				Label endMy;

				block1:
				if (s.initBci >= 0) {
					for (InsnNode node : mnCode.instructions) {
						MemberDescriptor d = node.descOrNull();
						if (d != null && d.owner != null) {
							if (d.name.equals("<init>") && (d.owner.equals(data.parent()) || d.owner.equals(data.name()))) {
								endMy = node.end();
								break block1;
							}
						}
					}
					throw new WeaveException(data.name()+" 存在错误: 无法找到替换构造器中<init>的调用");
				} else {
					endMy = Label.atZero();
				}

				InsnList replace = new InsnList();

				InsnList out = nxCode.instructions.copy();
				List<Label> jumps = s.headJump();
				// noinspection all
				if (endMy.getValue() == 0 && jumps.size() == 2 && jumps.get(1).getValue() == out.bci()) {
					out.replaceRange(jumps.get(0), jumps.get(1), replace, false);
				} else {
					replace.jump(mnCode.instructions.labelAt(endMy));

					for (int i = jumps.size()-1; i >= 0; i--) {
						Label end = jumps.get(i--);
						Label start = jumps.get(i--);
						out.replaceRange(start, end, replace, false);
					}
				}

				mnCode.instructions.replaceRange(Label.atZero(),endMy,out,false);

				mnCode.computeFrames(Code.COMPUTE_FRAMES);
				mnCode.stackSize = (char) Math.max(mnCode.stackSize, nxCode.stackSize);
				mnCode.localSize = (char) Math.max(mnCode.localSize, nxCode.localSize);

				copyLineNumbers(nxCode, mnCode, 0);
			}
			return input;
			case "INVOKE": {
				MemberDescriptor m = MemberDescriptor.fromJavapLike(s.extra.getString("matcher"));
				BitSet occurrences = s.getOccurrences();
				int ordinal = 0;

				List<InsnNode.InsnMod> rpls = new ArrayList<>();
				int isStatic = (s.method.modifier & ACC_STATIC);
				int used = 0;
				for (InsnList.NodeIterator itr = mnCode.instructions.iterator(); itr.hasNext(); ) {
					InsnNode node = itr.next();
					MemberDescriptor d = node.descOrNull();
					if (d != null && d.owner != null) {
						if ((m.owner.isEmpty() || m.owner.equals(d.owner)) &&
							d.name.equals(m.name) && d.rawDesc.equals(m.rawDesc)) {

							if (occurrences != null && !occurrences.contains(ordinal++)) continue;

							String desc;
							if ((node.opcode() == INVOKESTATIC) == isStatic > 0) {
								// 相同
								desc = d.rawDesc;
							} else if (node.opcode() != INVOKESTATIC) {
								// virtual -> static
								desc = "(L"+d.owner+';'+d.rawDesc.substring(1);
							} else {
								// static -> virtual NOT SUPPORTED!
								continue;
							}

							if (node.opcode() != INVOKEINTERFACE) {
								node.setOpcode(isStatic > 0 ? INVOKESTATIC : INVOKEVIRTUAL);
							}

							block:
							if (!s.method.rawDesc().equals(desc)) {
								List<Type> par1 = s.method.parameters();
								List<Type> par2 = Type.methodDesc(desc);
								par2.remove(par2.size()-1);
								if (par1.size()-1 == par2.size() && data.name().equals(par1.get(par1.size()-1).owner)) {
									if ((input.modifier&ACC_STATIC) != 0) throw new WeaveException("@Redirect.ContextInject 期待非静态方法: "+input.name()+"."+input.rawDesc());
									// context_inject

									itr = mnCode.instructions.since(node.bci());

									InsnList list = new InsnList();
									list.insn(ALOAD_0);
									node.insertBefore(list, false);

									break block;
								}

								throw new WeaveException("@Redirect "+m+" 参数不匹配: except "+desc+" , got "+s.method.rawDesc());
							}

							d.owner = data.name();
							d.name = s.method.name();
							d.rawDesc = s.method.rawDesc();

							// TODO 可能无法和aload_0一起使用
							if (node.opcode() == INVOKEINTERFACE) {
								InsnNode.InsnMod rpl = node.replace();
								rpl.list.invoke(isStatic > 0 ? INVOKESTATIC : INVOKEVIRTUAL, d.owner, d.name, d.rawDesc);
								rpls.add(rpl);
							}

							used++;
						}
					}
				}

				if (used < (occurrences == null ? 1 : occurrences.size()) && (s.flags & Inject.OPTIONAL) == 0)
					throw new WeaveException("@Redirect "+m+" 没有全部匹配("+used+")");

				for (int i = rpls.size() - 1; i >= 0; i--) rpls.get(i).commit();
			}
			return input;
			case "LDC": {
				int type = s.extra.getInt("matchType");
				String find = s.extra.getString("matchValue");

				String replace = s.extra.getString("replaceValue", "");
				InsnList replaceTo = new InsnList();
				if (!replace.isEmpty()) {
					switch (type) {
						case Constant.INT: replaceTo.ldc(Integer.parseInt(replace)); break;
						case Constant.LONG: replaceTo.ldc(Long.parseLong(replace)); break;
						case Constant.DOUBLE: replaceTo.ldc(Double.parseDouble(replace)); break;
						case Constant.FLOAT: replaceTo.ldc(Float.parseFloat(replace)); break;
						case Constant.CLASS: replaceTo.ldc(new CstClass(replace)); break;
						case Constant.STRING: replaceTo.ldc(replace); break;
						default: throw new WeaveException("OverwriteConstant的matchType必须是Constant中的有效类别ID");
					}
				} else {
					if ((s.method.modifier & ACC_STATIC) == 0) {
						replaceTo.insn(ALOAD_0);
						replaceTo.invokeV(data.name(), s.method.name(), s.method.rawDesc());
					} else {
						replaceTo.invokeS(data.name(), s.method.name(), s.method.rawDesc());
					}
				}

				BitSet occurrences = s.getOccurrences();
				CInt ordinal = new CInt();

				int used = 0;

				for (InsnNode node : mnCode.instructions) {
					switch (node.opcode()) {
						case ICONST_M1, ICONST_0:
						case ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5:
							if (type == Constant.INT && matchLdcByOpcode(node, ICONST_0, find, occurrences, ordinal)) {
								node.replace(replaceTo, false);
								used++;
							}
						break;
						case LCONST_0, LCONST_1:
							if (type == Constant.LONG && matchLdcByOpcode(node, LCONST_0, find, occurrences, ordinal)) {
								node.replace(replaceTo, false);
								used++;
							}
						break;
						case FCONST_0, FCONST_1, FCONST_2:
							if (type == Constant.FLOAT && matchLdcByOpcode(node, FCONST_0, find, occurrences, ordinal)) {
								node.replace(replaceTo, false);
								used++;
							}
						break;
						case DCONST_0, DCONST_1:
							if (type == Constant.DOUBLE && matchLdcByOpcode(node, DCONST_0, find, occurrences, ordinal)) {
								node.replace(replaceTo, false);
								used++;
							}
						break;
						case BIPUSH, SIPUSH:
							if (type == Constant.INT && find.equals(String.valueOf(node.getNumberExact())) &&
								(occurrences == null || occurrences.contains(++ordinal.value))) {
								node.replace(replaceTo, false);
								used++;
							}
						break;
						case LDC, LDC_W, LDC2_W:
							if (type == node.constant().type() && find.equals(node.constant().getEasyCompareValue()) &&
								(occurrences == null || occurrences.contains(++ordinal.value))) {
								node.replace(replaceTo, false);
								used++;
							}
						break;
					}
				}

				if (used < (occurrences == null ? 1 : occurrences.size()) && (s.flags & Inject.OPTIONAL) == 0)
					throw new WeaveException("@OverwriteConstant "+find+" 没有全部匹配("+used+")");
			}
			return input;
			case "TAIL": {
				if (s.initBci >= 0) throw new WeaveException(MARKER_InitSuper+"不能在TAIL使用");

				InsnList insn = mnCode.instructions;
				InsnList hookInsn = nxCode.instructions.copy();
				int paramSize = TypeHelper.paramSize(input.rawDesc());

				// 寻找空位放返回值和变量
				BitSet usedSlots = null;
				int retValId = -1;

				InsnList tmpList = new InsnList();

				Type ret = input.returnType();
				int returnHookType;
				if (ret.type == Type.VOID) {
					if (!s.retVal().isEmpty()) throw new WeaveException("VOID返回值不能使用"+MARKER_ReturnValue);
					returnHookType = 0x100; // JUMP
				} else if (!s.retVal().isEmpty()) {
					returnHookType = ret.getOpcode(ISTORE);
					if (s.retVal().size() == 1 && s.retVal().get(0).bci() == 0) {
						returnHookType |= 0x100; // JUMP
					} else {
						returnHookType |= 0x200; // STORE_AND_JUMP

						usedSlots = findUsedVarIds(insn, paramSize);
						retValId = nextAvailableVarId(usedSlots, ret.opcodePrefix());
						tmpList.varLoad(ret, retValId);
					}
				} else {
					returnHookType = 0x000; // POP_JUMP
				}

				for (InsnNode node : hookInsn) {
					MemberDescriptor desc = node.descOrNull();
					if (desc != null && desc.name.startsWith(MARKER_ReturnValue)) {
						node.replace(tmpList, true);
					}
				}

				int begin;
				parBR:
				if (s.assignId != null) {
					Int2IntMap overwriteCheck = new Int2IntMap();

					for (ToIntMap.Entry<InsnNode> entry : s.assignId.selfEntrySet()) {
						overwriteCheck.put(entry.value, -1);
						String name = entry.getKey().opName();
						if (name.charAt(0) == 'D' || name.charAt(0) == 'L')
							overwriteCheck.put(entry.value +1, -2);
					}

					// 备份参数，计算tail用到的参数id(已完成)，若修改过则暂存到新的变量中再恢复
					tmpList.clear();
					for (InsnNode node : insn) {
						String name = node.opName();
						int vid = node.getVarId();
						if (vid >= 0 && name.startsWith("Store", 1)) {
							Int2IntMap.Entry entry = overwriteCheck.getEntry(vid);
							if (entry != null && entry.v == -1) {
								if (usedSlots == null) usedSlots = findUsedVarIds(insn, paramSize);
								int slot = nextAvailableVarId(usedSlots, name);

								int store = node.opcode()&0xFF;
								if (store >= 0x3b) store = 0x36 + (store-0x3b) / 4;
								tmpList.vars((byte) (store - (ISTORE - ILOAD)), vid);
								tmpList.vars((byte) store, slot);

								entry.setIntValue((store << 16) | slot);
							}
						}
					}

					begin = tmpList.bci();
					if (begin == 0) break parBR;

					insn.replaceRange(0,0,tmpList,false);

					tmpList.clear();
					for (Int2IntMap.Entry entry : overwriteCheck.selfEntrySet()) {
						int to = entry.getIntValue();
						if (to >= 0) {
							// 从备份中读取
							byte store = (byte) (to >>> 16);
							byte load = (byte) (store - (ISTORE - ILOAD));

							tmpList.vars(load, to&0xFFFF);
							tmpList.vars(store, entry.getIntKey());
						}
					}

					hookInsn.replaceRange(0, 0, tmpList, false);
				} else {
					begin = 0;
				}

				Label hookBegin = hookInsn.labelAt(0);
				int end = insn.bci();
				insn.replaceRange(end, end, hookInsn, false);

				for (InsnList.NodeIterator it = insn.since(begin); it.hasNext(); ) {
					InsnNode node = it.next();
					if (node.bci() >= end) break;

					if (node.opName().endsWith("Return")) {
						tmpList.clear();

						switch (returnHookType>>>8) {
							case 0: tmpList.insn(POP); break; // 放弃返回值
							case 1: break; // 形如int v=$$VALUE_I(),并且就在方法开头  或者  是VOID 直接跳
							case 2: tmpList.vars((byte) returnHookType, retValId); break; // 储存至变量
						}

						if (node.bci()+node.length() < end) {
							tmpList.jump(hookBegin);
							end += tmpList.bci() - node.length();
						}

						node.replace(tmpList, false);
						it = insn.since(node.bci());
					}
				}

				mnCode.computeFrames(Code.COMPUTE_FRAMES);
				mnCode.stackSize = (char) Math.max(mnCode.stackSize, nxCode.stackSize);
				mnCode.localSize = (char) Math.max(mnCode.localSize, nxCode.localSize);
				if ((returnHookType & 0x100) != 0) mnCode.stackSize = (char) Math.max(mnCode.stackSize, 1);
				if (usedSlots != null) mnCode.localSize = (char) Math.max(mnCode.localSize, usedSlots.last());

				copyLineNumbers(nxCode, mnCode, end+begin);
			}
			return input;
			case "SUPER": {
				data.methods.add(s.method);
				String mapName = "nx^SIJ@@"+randomId((System.nanoTime() << 32) | s.mapName.hashCode());
				input.name(mapName);
				for (Map.Entry<Label, Object> entry : nxCode.instructions.nodeData()) {
					if (entry.getValue().getClass() == MemberDescriptor.class) {
						MemberDescriptor d = (MemberDescriptor) entry.getValue();
						if ((data.name().equals(d.owner) || d.owner.contains("SIJ")) && s.mapName.equals(d.name) && s.method.rawDesc().equals(d.rawDesc)) {
							d.owner = data.name();
							d.name = mapName;
						}
					}
				}
			}
			return input;
			case "PATTERN":
				InsnList toFind = mnCode.instructions;
				InsnList matcher = s.matcher;
				InsnMatcher ctx = new InsnMatcher() {
					int iPos = -2;

					@Override
					public boolean isNodeSimilar(InsnNode haystack, InsnNode needle) {
						if (needle.opcode() == INVOKESTATIC) {
							MemberDescriptor d = needle.desc();
							if (d.name.startsWith(MARKER_AnyVariable)) {
								int vid = haystack.getVarId();
								if (vid < 0) {
									failed = true;
									return false;
								}

								return checkId(iPos--, vid);
							}
						}
						return super.isNodeSimilar(haystack, needle);
					}
				};

				InsnNode matchFirst = matcher.getNodeAt(0);
				for (InsnNode node : toFind) {
					ctx.reset();

					findFirst:
					if (ctx.isNodeSimilar(node, matchFirst)) {
						InsnList.NodeIterator
							itrA = toFind.since(node.bci()+node.length()),
							itrB = matcher.since(matchFirst.length());
						while (itrA.hasNext()&&itrB.hasNext()) {
							InsnNode A = itrA.next(), B = itrB.next();
							if (!ctx.isNodeSimilar(A, B)) {
								System.out.println("second failed on "+A+" and "+B);
								break findFirst;
							}
						}

						System.out.println("successfully match sequence "+toFind.copySlice(node.pos(), itrA.unsharedPos()));
						// found
						InsnList toInj = nxCode.instructions.copy();
						ctx.mapVarId(toInj);

						toFind.replaceRange(toFind.labelAt(node.pos()), itrA.unsharedPos(), toInj, false);

						mnCode.computeFrames(Code.COMPUTE_FRAMES);
						mnCode.stackSize = (char) Math.max(mnCode.stackSize, nxCode.stackSize);
						mnCode.localSize = (char) Math.max(mnCode.localSize, nxCode.localSize);

						copyLineNumbers(nxCode, mnCode, node.bci());
						return input;
					}
				}
				throw new WeaveException("PATTERN没有匹配成功: 目标方法: "+mnCode+"\n匹配方法: "+matcher);
		}
	}
	private static BitSet findUsedVarIds(InsnList insn, int paramSize) {
		BitSet usedSlots = new BitSet();
		usedSlots.fill(paramSize);
		for (InsnNode node : insn) {
			String name = node.opName();
			int vid = node.getVarId();
			if (vid >= paramSize && name.startsWith("Store", 1)) {
				usedSlots.add(vid++);
				if (name.charAt(0) == 'D' || name.charAt(0) == 'L') {
					usedSlots.add(vid);
				}
			}
		}
		return usedSlots;
	}
	private static int nextAvailableVarId(BitSet usedSlots, String name) {
		int newSlot = 0;
		int len = name.charAt(0) == 'D' || name.charAt(0) == 'L' ? 2 : 1;
		while (!usedSlots.allFalse(newSlot, newSlot+len)) {
			newSlot++;
		}
		usedSlots.addRange(newSlot, newSlot+len);
		return newSlot;
	}
	private static boolean matchLdcByOpcode(InsnNode node, byte base, String find, BitSet occurrences, CInt ordinal) {
		int value = node.opcode() - base;
		if (!find.equals(String.valueOf(value))) return false;
		return occurrences == null || occurrences.contains(++ordinal.value);
	}
	private static void copyLineNumbers(Code from, Code to, int pcOff) {
		LineNumberTable lnFr = (LineNumberTable) from.getRawAttribute("LineNumberTable");
		LineNumberTable lnTo = (LineNumberTable) to.getRawAttribute("LineNumberTable");

		if (lnTo == null) {
			if (lnFr == null) return;
			lnTo = new LineNumberTable();
		}

		List<LineNumberTable.Item> list = lnTo.list;
		for (int i = list.size()-1; i >= 0; i--) {
			LineNumberTable.Item item = list.get(i);
			if (!item.pos.isValid()) list.remove(i);
		}

		if (lnFr == null) return;

		BitSet pcMap = to.instructions.getPcMap();
		for (LineNumberTable.Item item : lnFr.list) {
			int value = item.pos.getValue();
			if (pcMap.contains(value+pcOff)) {
				// TODO copy lines better (consider hole)
				list.add(new LineNumberTable.Item(to.instructions.labelAt(value+pcOff), item.getLine()));
			}
		}

		lnTo.sort();
	}
	private static void removeLVT(Code code) {
		AttributeList list = code.attributesNullable();
		if (list != null) {
			list.removeByName("LocalVariableTable");
			list.removeByName("LocalVariableTypeTable");
		}
	}
	//endregion
	private static String randomId(long v) {
		CharList sb = IOUtil.getSharedCharBuf();
		v &= Long.MAX_VALUE;
		while (v != 0) {
			sb.append((char)TextUtil.digits[(int) (v%62)]);
			v /= 62;
		}
		return sb.toString();
	}

	//region override
	public Map<String, Patch> registry() { return registry; }
	protected boolean shouldApply(String annotation, Attributed node, AList args) { throw new UnsupportedOperationException(); }
	protected String mapName(String owner, String newOwner, String name, MemberNode node) { return name; }
	//endregion
}