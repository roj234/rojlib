package roj.asmx.nixim;

import org.jetbrains.annotations.NotNull;
import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.cp.*;
import roj.asm.frame.Frame2;
import roj.asm.frame.Var2;
import roj.asm.tree.*;
import roj.asm.tree.anno.AnnVal;
import roj.asm.tree.anno.AnnValString;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.*;
import roj.asm.type.Desc;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.util.ClassUtil;
import roj.asm.util.Context;
import roj.asm.util.InsnHelper;
import roj.asm.visitor.*;
import roj.asmx.ITransformer;
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
 * Nixim class injector 3.1
 * @author Roj234
 * @since 2023/10/9 18:49
 */
public class NiximSystemV2 implements ITransformer {
	public static final String A_NIXIM_CLASS_FLAG = unifyClassName(Nixim.class.getName());
	public static final String A_INJECT = unifyClassName(Inject.class.getName());
	public static final String A_SHADOW = unifyClassName(Shadow.class.getName());
	public static final String A_COPY = unifyClassName(Copy.class.getName());
	public static final String A_DYNAMIC = unifyClassName(Dynamic.class.getName());
	public static final String A_UNIQUE = unifyClassName(Unique.class.getName());
	public static final String A_FINAL = unifyClassName(Final.class.getName());

	// region SPECIAL_METHODS
	private static final String
		SPEC_M_ANYVAR = "$$$VALUE",
		SPEC_M_RETVAL = "$$$VALUE",
		SPEC_M_CONTINUE = "$$$VALUE",
		SPEC_M_CONSTRUCTOR = "$$$CONSTRUCTOR",
		SPEC_M_CONSTRUCTOR_THIS = "$$$CONSTRUCTOR_THIS";

	/**
	 * matcher find region begin
	 */
	public static void $$$MATCH_BEGIN() {}
	/**
	 * matcher find region end
	 */
	public static void $$$MATCH_END() {}

	/**
	 *   <table border="1" cellspacing="2" cellpadding="2">
	 *     <tr>
	 *       <th colspan="9"><span style="font-weight:normal">
	 *        这些以{@link NiximSystemV2#SPEC_M_RETVAL}开头的方法()在Inject注解的函数中拥有特殊意义 <br>
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
	 *       <td>{@link SearchReplace}</td>
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

	public static final class NiximData {
		public final String self;
		public String target;

		public List<String> impls = Collections.emptyList();

		public Map<Pcd, InjectState> inject = Collections.emptyMap();
		final Map<Pcd, InjectState> inject() { if (inject.isEmpty()) inject = new MyHashMap<>(); return inject; }

		public MethodNode copyInit;
		public Set<CNode> copied = Collections.emptySet();
		final Set<CNode> copied() { if (copied.isEmpty()) copied = new MyHashSet<>(Hasher.identity()); return copied; }

		// Shadow
		public final MyHashSet<Pcd> preconditions = new MyHashSet<>();

		// lambda
		public Map<BootstrapMethods.Item, List<Desc>> lambda = Collections.emptyMap();

		NiximData next;

		public NiximData(String self) { this.self = self; }

		@Override
		public String toString() { return "Nixim{'"+self+"' => '"+target+"'}"; }
	}
	static final int SKIP_STATIC_CHECK = 16384;
	static final class InjectState {
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

		// Middle
		XInsnList matcher;

		// Tail
		ToIntMap<XInsnNodeView> assignId;
		@SuppressWarnings("unchecked")
		public List<XInsnNodeView> retVal() { return (List<XInsnNodeView>) nodeList; }

		Annotation extra;

		InjectState next;

		InjectState(MethodNode method, Annotation map) {
			this.method = method;

			flags = map.getInt("flags", 0);

			boolean isAbstract = method.attrByName("Code") == null;
			if (map.type().endsWith("OverwriteConstant")) {
				at = "LDC";
				extra = map;
			} else if (map.type().endsWith("InvokeRedirect")) {
				at = "INVOKE";
				extra = map;
			} else if (map.type().endsWith("SearchReplace")) {
				at = "MIDDLE";
				extra = map;
			} else {
				at = map.getEnumValue("at", "OLD_SUPER_INJECT");
				switch (at) {
					case "REMOVE": isAbstract = false; break;
					case "HEAD", "TAIL": nodeList = new ArrayList<>(); break;
					default: break;
				}
			}

			if (isAbstract) throw new IllegalArgumentException("方法不能是抽象的 " + method);
			mapName = map.getString("value", method.name());
		}

		public MyBitSet getOccurrences() {
			int[] a = extra.getIntArray("occurrences");
			return a == null || a.length == 0 ? null : MyBitSet.from(a);
		}
	}

	protected final Map<String, NiximData> registry = new MyHashMap<>();

	public final void load(ConstantData data) throws NiximException {
		NiximData nx = read(data);
		if (nx == null) return;
		if (registry.putIfAbsent(nx.self, nx) != null) {
			throw new NiximException("Nixim类"+nx.self+"已存在！");
		}
		nx.next = registry.put(nx.target, nx);
	}
	public final boolean unloadBySource(String source) {
		NiximData self = registry.remove(source);
		if (self == null) return false;

		NiximData nx = registry.get(self.target), prev = null;
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

	public static boolean transformNiximUser(ConstantPool cp, NiximData nx, Map<String, NiximData> ctx) {
		boolean changed = false;
		AbstractMap<String, String> fakeMap = getFakeMap(nx, ctx);

		Pcd tmpPCD = new Pcd();
		List<Constant> constants = cp.array();
		for (int i = 0; i < constants.size(); i++) {
			Constant c = constants.get(i);
			switch (c.type()) {
				case Constant.METHOD:
				case Constant.INTERFACE:
				case Constant.FIELD:
					CstRef ref = (CstRef) c;
					NiximData nx1 = nx!=null&&ref.className().equals(nx.self) ? nx : ctx.get(ref.className());
					if (nx1 != null && nx1.self.equals(ref.className())) {
						tmpPCD.name = ref.descName();
						tmpPCD.desc = ref.descType();
						Pcd pcd = nx1.preconditions.find(tmpPCD);
						if (pcd != tmpPCD) {
							changed = true;

							if (!pcd.mapOwner.equals(ref.className())) ref.clazz(cp.getClazz(pcd.mapOwner));
							if (!pcd.mapName.equals(pcd.name)) ref.desc(cp.getDesc(pcd.mapName, ref.descType()));

							CstUTF desc = ref.desc().getType();
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
				String name = ref1.name().str();
				NiximData nx1 = ctx.get(name);
				if (nx1 != null && nx1.self.equals(name)) {
					changed = true;

					cp.setUTFValue(ref1.name(), nx1.target);
				}
			}
		}

		return changed;
	}
	private static AbstractMap<String, String> getFakeMap(NiximData nx, Map<String, NiximData> ctx) {
		return new AbstractMap<>() {
			@Override
			public String get(Object key) {
				if (nx != null && key.equals(nx.self)) return nx.target;

				NiximData nx = ctx.get(key);
				return nx == null ? null : nx.target;
			}

			@NotNull
			@Override
			public Set<Entry<String, String>> entrySet() {return Collections.emptySet();}
		};
	}

	public NiximData read(ConstantData data) throws NiximException {
		if (CLASS_DUMP) dump("nixim_mapping", data);

		NiximData nx = new NiximData(data.name);

		Map<String, Annotation> annotation = getAnnotations(data,data);
		Annotation a = annotation.get(A_NIXIM_CLASS_FLAG);
		if (a == null) throw new NiximException(data.name + " 不是有效的Nixim class （没有找到注解）");

		nx.target = unifyClassName(a.getString("value"));
		if (nx.target.equals("/")) nx.target = data.parent;

		if (a.getBoolean("copyItf", true)) nx.impls = ArrayUtil.copyOf(data.interfaces());

		//int flag = a.getInt("flags");

		a = annotation.get(A_DYNAMIC);
		if (a != null) {
			List<AnnVal> annVals = a.getArray("value");
			if (!shouldApply(A_NIXIM_CLASS_FLAG, data, Helpers.cast(annVals))) {
				return null;
			}
		}

		// 检测特殊方法, 删除桥接方法
		SimpleList<MethodNode> autoCopy = new SimpleList<>();
		SimpleList<MethodNode> methods = data.methods;
		for (int i = methods.size() - 1; i >= 0; i--) {
			MethodNode method = methods.get(i);
			String name = method.name();
			if (name.startsWith("$$$")) {
				if (method.attrByName(Attribute.ClAnnotations.name) != null)
					throw new NiximException("特殊方法("+name+")不能包含注解");

				if (!name.startsWith(SPEC_M_CONSTRUCTOR)) {
					if (0 == (method.modifier & ACC_STATIC)) throw new NiximException("特殊方法("+name+")必须静态");
					if (!method.rawDesc().startsWith("()")) throw new NiximException("特殊方法("+name+")不能有参数");
				} else if (!method.rawDesc().endsWith(")V")) {
					throw new NiximException("构造器标记("+name+")必须返回void");
				} else if (0 != (method.modifier & ACC_STATIC)) throw new NiximException("构造器标记("+name+")不能静态");
			}

			if (0 != (method.modifier & ACC_SYNTHETIC))
				autoCopy.add(methods.get(i));
		}

		Map<String, NiximData> ctx = registry();
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

		transformNiximUser(data.cp, nx, ctx);

		// 查找无法访问的方法
		MyHashSet<Desc> inaccessible = new MyHashSet<>();
		boolean isSamePackage = ClassUtil.arePackagesSame(data.name, nx.target);
		addInaccessible(data, data.methods, inaccessible, isSamePackage);
		addInaccessible(data, data.fields, inaccessible, isSamePackage);

		// 复制走clinit
		int clInit = data.getMethod("<clinit>", "()V");
		if (clInit >= 0) {
			MethodNode mn = data.methods.remove(clInit);
			nx.copied().add(mn);
			nx.copyInit = mn;
		}

		List<BootstrapMethods.Item> lambdaPending = new SimpleList<>();

		// 检测无法访问的方法(nixim中private等), 循环添加autoCopy中的方法, 并复制用到的lambda
		MyCodeVisitor cv = new MyCodeVisitor(data, inaccessible, nx, autoCopy);
		for (InjectState state : nx.inject.values()) {
			do {
				cv.state = state;
				cv.MyVisit(state.method);

				XAttrCode code = (XAttrCode) state.method.parsed(data.cp).attrByName("Code");
				if (code != null) checkLambda(data, code, nx, lambdaPending);

				if (state.initBci == 0 && state.mapName.equals("<init>"))
					throw new NiximException("没有找到 "+SPEC_M_CONSTRUCTOR+"或"+SPEC_M_CONSTRUCTOR_THIS+" in "+state.method);

				try {
					prepareInject(state, data, nx);
				} catch (NiximException e) {
					throw new NiximException("处理方法"+data.name+"."+state.method.name()+"时出现了错误: ", e);
				}

				state = state.next;
			} while (state != null);
		}
		cv.state = null;

		for (CNode node : nx.copied) {
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
					if (!ref.className().equals(data.name)) break;

					List<MethodNode> nodes = data.methods;
					for (int j = 0; j < nodes.size(); j++) {
						MethodNode mn = nodes.get(j);
						if (mn.descMatch(ref.desc())) {
							Pcd pcd = new Pcd();
							pcd.name = mn.name();
							pcd.desc = mn.rawDesc();
							pcd.mapOwner = nx.target;
							pcd.mapName = "nx^lambda@"+randomId((System.nanoTime() << 32) | mn.name().hashCode());
							nx.preconditions.add(pcd);

							// name changed in hasNewCopyMethod
							ref.desc(data.cp.getDesc(pcd.mapName, mn.rawDesc()));

							// noinspection all
							nodes.remove(j);

							// 循环添加。
							cv.copied.add(mn);
							break find;
						}
					}

					throw new NiximException("无法找到符合条件的 lambda 方法: "+ref.desc());
				}
			}
			lambdaPending.clear();

			hasNew = false;
			for (MethodNode node : cv.copied) {
				Attribute cc = node.attrByName("Code");
				if (cc == null || cc.getClass() != AttrUnknown.class) continue;

				cv.MyVisit(node);
				nx.copied().add(node);
				hasNew = true;

				XAttrCode code = (XAttrCode) node.parsed(data.cp).attrByName("Code");
				if (code != null) checkLambda(data, code, nx, lambdaPending);
			}
		} while (hasNew || !lambdaPending.isEmpty());

		boolean hasNewCopyMethod = false;
		Pcd tmpPcd = new Pcd();
		Map<String, String> map = Collections.singletonMap(nx.self, nx.target);
		for (CNode node : nx.copied) {
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
			fixNewCopyMethod(tmpPcd, nx, nx.copied);

		List<CNode> list = new SimpleList<>(nx.inject.values().size());
		for (InjectState t : nx.inject.values()) list.add(t.method);
		fixNewCopyMethod(tmpPcd, nx, list);

		// special case
		nx.copied.remove(nx.copyInit);

		// fields
		for (CNode node : nx.copied) node.parsed(data.cp);
		return nx;
	}
	// region Utilities for reading Nixim classes
	private static void fixNewCopyMethod(Pcd tmp, NiximData nx, Collection<CNode> nodes) {
		for (CNode n : nodes) {
			if (!(n instanceof MethodNode)) continue;
			XAttrCode code = n.parsedAttr(null, Attribute.Code);
			if (code == null) continue;

			for (Map.Entry<Label, Object> entry : code.instructions.nodeData()) {
				if (entry.getValue().getClass() == Desc.class) {
					Desc desc = (Desc) entry.getValue();
					if (desc.name.equals(nx.self)) {
						tmp.name = desc.name;
						tmp.desc = desc.param;
						Pcd pcd = nx.preconditions.find(tmp);
						if (pcd != tmp && pcd.mapName != null) {
							desc.owner = pcd.mapOwner;
							desc.name = pcd.mapName;
						}
					}
				}
			}

			// TODO well, maybe automatic calculation future
			List<Frame2> frames = code.frames;
			if (frames != null) {
				for (Frame2 frame : frames) {
					for (Var2 v : frame.stacks)
						if (nx.self.equals(v.owner))
							v.owner = nx.target;
					for (Var2 v : frame.locals)
						if (nx.self.equals(v.owner))
							v.owner = nx.target;
				}
			}
		}
	}
	private static void prepareInject(InjectState s, ConstantData data, NiximData nx) throws NiximException {
		MethodNode method = s.method;
		XAttrCode code = method.parsedAttr(data.cp, Attribute.Code);
		if (s.initBci > 0) {
			LineNumberTable ln = (LineNumberTable) code.attrByName("LineNumberTable");
			if (ln != null) {
				for (XInsnNodeView node : code.instructions) {
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
				for (XInsnNodeView node : code.instructions) {
					int id = node.getVarId();
					if (id >= 0) {
						if (node.opName().startsWith("Store", 1)) {
							if (id >= paramLength) continue;

							changedParam = true;
						}
					} else if (node.opName().endsWith("Return")) {
						XInsnNodeView node1 = node.prev();
						if (node1.opcode() == INVOKESTATIC && node1.desc().name.startsWith(SPEC_M_CONTINUE)) {
							if (!TypeHelper.parseReturn(node1.desc().param).equals(method.returnType()))
								throw new NiximException("返回值指代#"+s.retVal().size()+"(bci: "+node1.bci()+")的返回值不适用于目的方法的"+method.returnType());

							usedContinue.add(code.instructions.labelAt(node1.pos()));
							usedContinue.add(code.instructions.labelAt(node.end()));
						}
					}
				}

				if (usedContinue.isEmpty() && !changedParam) throw new NiximException("Head注入未用到CONTINUE, 你应该使用REPLACE模式");
			}
			break;
			case "INVOKE":
				// nothing to check
				break;
			case "LDC":
				checkLdcMatch(s, s.extra.getString("matchValue"));
				String value = s.extra.getString("replaceValue", "");
				if (!value.isEmpty()) checkLdcMatch(s, value);
				break;
			case "TAIL": {
				ToIntMap<XInsnNodeView> usedVar = new ToIntMap<>();
				int paramLength = TypeHelper.paramSize(method.rawDesc());

				for (XInsnNodeView node : code.instructions) {
					int id = node.getVarId();
					if (id >= 0) {
						int bci = node.bci();
						if (node.opName().startsWith("Load", 1)) {
							if (id >= paramLength) continue;

							usedVar.putInt(node.unshared(), id);
						} else if (bci > 0) {
							node = node.prev();
							if (node.opcode() == INVOKESTATIC && node.desc().name.startsWith(SPEC_M_RETVAL)) {
								if (!TypeHelper.parseReturn(node.desc().param).equals(method.returnType()))
									throw new NiximException("返回值指代#"+s.retVal().size()+"(bci: "+node.bci()+")的返回值不适用于目的方法的"+method.returnType());
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
			case "OLD_SUPER_INJECT":
				if ((s.flags & 65536) == 0) {
					s.at = "REPLACE";
				}
				break;
			case "MIDDLE":
				String matcher = s.extra.getString("matcher");
				MethodNode mn = data.getMethodObj(matcher);
				if (mn == null) throw new NiximException("未找到matcher:"+matcher);
				// search
				s.matcher = makeMatcher(data, mn, null);
				// replace
				s.method.parsedAttr(data.cp, Attribute.Code).instructions = makeMatcher(data, s.method, s);
				break;
		}
	}
	private void readAnnotations(ConstantData data, NiximData nx, SimpleList<? extends CNode> nodes) throws NiximException {
		Annotation a;
		Map<String, Annotation> map;

		for (int i = nodes.size()-1; i >= 0; i--) {
			CNode node = nodes.get(i);
			if (node.name().startsWith("$$$")) {
				nodes.remove(i);
				continue;
			}

			Pcd pcd = null;

			map = getAnnotations(node, data);
			Annotation dyn = map.get(A_DYNAMIC);

			a = map.get(A_SHADOW);
			if (a != null) {
				if (annotationPreCheck(data, a, map, node, null, dyn)) continue;

				pcd = new Pcd();
				pcd.name = node.name();
				pcd.desc = node.rawDesc();

				pcd.mapName = a.getString("value", pcd.name);
				if (pcd.mapName.equals("/")) pcd.mapName = pcd.name;
				pcd.mapOwner = unifyClassName(a.getString("owner", nx.target));

				pcd.mode = Pcd.SHADOW | node.modifier;

				nx.preconditions.add(pcd);

				if ((a.getInt("flags") & Inject.RUNTIME_MAP) != 0)
					pcd.mapName = mapName(data.name, pcd.mapOwner, pcd.mapName, node);

				a = map.get(A_FINAL);
				if (a != null) {
					pcd.mode |= ACC_FINAL;
					if (a.containsKey("setFinal")) {
						if (a.getBoolean("setFinal")) throw new NiximException("Shadow不能配Final(setFinal=true): "+data.name+'.'+node.name()+": "+map.values());
						pcd.mode |= Pcd.REAL_DEL_FINAL;
					}
				}

				nodes.remove(i);
			}

			a = map.get(A_COPY);
			if (a != null) {
				if (annotationPreCheck(data, a, map, node, pcd, dyn)) continue;

				pcd = new Pcd();
				pcd.name = node.name();
				pcd.desc = node.rawDesc();

				pcd.mapName = a.getString("value", pcd.name);
				pcd.mapOwner = nx.target;

				pcd.mode = Pcd.COPY | node.modifier;

				nx.preconditions.add(pcd);

				if ((a.getInt("flags") & Inject.RUNTIME_MAP) != 0)
					pcd.mapName = mapName(data.name, pcd.mapOwner, pcd.mapName, node);

				boolean method = pcd.name.startsWith("(");

				a = map.get(A_UNIQUE);
				if (a != null) {
					pcd.mapName = "nx^copy@"+randomId(System.nanoTime() << 32 | node.name().hashCode());
				}

				a = map.get(A_FINAL);
				if (a != null) {
					if (method) throw new NiximException("Copy&Final的组合不能用在方法上: "+data.name+'.'+node.name()+": "+map.values());
					pcd.mode |= ACC_FINAL;
					if (a.containsKey("setFinal")) {
						pcd.mode |= a.getBoolean("setFinal") ? Pcd.REAL_ADD_FINAL : Pcd.REAL_DEL_FINAL;
					}
				}

				nx.copied().add(node);
				nodes.remove(i);
			}

			a = map.get(A_INJECT);
			if (a == null) a = map.get("roj/asmx/nixim/InvokeRedirect");
			if (a == null) a = map.get("roj/asmx/nixim/OverwriteConstant");
			if (a == null) a = map.get("roj/asmx/nixim/SearchReplace");
			if (a != null) {
				if (annotationPreCheck(data, a, map, node, pcd, dyn)) continue;

				pcd = new Pcd();
				pcd.name = node.name();
				pcd.desc = a.getString("injectDesc", node.rawDesc());

				pcd.mapName = a.getString("value", pcd.name);
				pcd.mapOwner = nx.target;

				pcd.mode = Pcd.INJECT | node.modifier;

				if ((a.getInt("flags") & Inject.RUNTIME_MAP) != 0)
					pcd.mapName = mapName(data.name, pcd.mapOwner, pcd.mapName, node);

				pcd.name = pcd.mapName;
				InjectState state = new InjectState((MethodNode) node, a);
				InjectState prev = nx.inject().put(pcd, state);
				if (prev != null) state.next = prev;
				nodes.remove(i);

				if (a.type().equals("roj/asmx/nixim/InvokeRedirect")) {
					nx.copied().add(node);
					state.flags |= SKIP_STATIC_CHECK;
				}
			}
		}
	}
	private boolean annotationPreCheck(ConstantData data, Annotation a, Map<String, Annotation> annotation, CNode node, Pcd pcd, Annotation dyn) throws NiximException {
		if (pcd != null) throw new NiximException("冲突的注解: "+data.name+'.'+node+": "+annotation.values());
		if (dyn != null) {
			List<AnnVal> annVals = dyn.getArray("value");
			if (!shouldApply(a.type(), data, Helpers.cast(annVals))) return true;
		}
		if (node.name().equals("<init>"))
			throw new NiximException("不支持操作: 自Nixim3.0起,你不能在构造器中使用Inject注解: "+data.name+'.'+node+"\n" +
				"替代方案: 使用非静态方法，Inject到<init>，并调用"+SPEC_M_CONSTRUCTOR+"或"+SPEC_M_CONSTRUCTOR_THIS+"\n" +
				"为何删除: 1. 容易造成误解，2.无法使用this(...)");
		return false;
	}
	private static void addInaccessible(ConstantData data, SimpleList<? extends CNode> nodes, MyHashSet<Desc> inaccessible, boolean isSamePackage) {
		for (int i = 0; i < nodes.size(); i++) {
			CNode remain = nodes.get(i);
			int acc = remain.modifier;
			if (((acc & (ACC_PRIVATE | ACC_STATIC)) != ACC_STATIC) || ((acc & ACC_PUBLIC) == 0 && !isSamePackage)) {
				inaccessible.add(new Desc(data.name, remain.name(), remain.rawDesc()));
			}
		}
	}
	private static void checkLambda(ConstantData data, XAttrCode code, NiximData nx, List<BootstrapMethods.Item> pending) throws NiximException {
		for (Map.Entry<Label, Object> entry : code.instructions.nodeData()) {
			Object o = entry.getValue();
			if (o.getClass() == Desc.class) {
				Desc desc = (Desc) o;
				if (desc.owner == null) {
					BootstrapMethods bsm = data.parsedAttr(data.cp, Attribute.BootstrapMethods);
					if (bsm == null) throw new NiximException(data.name+"存在错误,BootstrapMethods不存在");

					BootstrapMethods.Item key = bsm.methods.get(desc.flags);
					// NiximClass不应有实例。 (static method也不建议使用)
					desc.param = ClassUtil.getInstance().mapMethodParam(Collections.singletonMap(data.name, nx.target), desc.param);

					if (nx.lambda.isEmpty()) nx.lambda = new MyHashMap<>();
					List<Desc> list = nx.lambda.computeIfAbsent(key, Helpers.fnArrayList());
					if (list.isEmpty()) pending.add(key);
					list.add(desc);
				}
			}
		}
	}
	private static void checkLdcMatch(InjectState s, String data1) throws NiximException {
		switch (s.extra.getInt("matchType", 0)) {
			case Constant.INT: Integer.parseInt(data1); break;
			case Constant.LONG: Long.parseLong(data1); break;
			case Constant.DOUBLE: Double.parseDouble(data1); break;
			case Constant.FLOAT: Float.parseFloat(data1); break;
			case Constant.CLASS: TypeHelper.parseField(data1); break;
			case Constant.STRING: break;
			default: throw new NiximException("OverwriteConstant的matchType必须是Constant中的有效类别ID");
		}
	}
	private static XInsnList makeMatcher(ConstantData data, MethodNode m, InjectState state) throws NiximException {
		XAttrCode code = m.parsedAttr(data.cp, Attribute.Code);
		if (code == null) throw new NiximException("方法"+m.name()+"不能是抽象的");

		Label start = null, end = null;
		label:
		for (XInsnNodeView node : code.instructions) {
			Desc desc = node.descOrNull();
			if (desc != null) {
				switch (desc.name) {
					case "$$$MATCH_BEGIN":
						if (start != null) throw new NiximException("重复的MATCH_BEGIN");
						start = code.instructions.labelAt(node.end());
					break;
					case "$$$MATCH_END":
						end = code.instructions.labelAt(node.pos());
					break label;
					case SPEC_M_CONTINUE:
						if (state == null) throw new NiximException("Continue不能在这里使用");
						Label pos = code.instructions.labelAt(node.pos());
						state.headJump().add(pos);
						state.headJump().add(pos);
					break;
				}
			}
		}

		if (start == null || end == null) throw new NiximException(m.name()+"不是有效的Match/Replace方法");
		return code.instructions.copySlice(start, end);
	}
	// endregion




	@Override
	public boolean transform(String name, Context ctx) throws NiximException {
		if (registry.isEmpty()) return false;

		NiximData nx1 = registry.get(name);
		if (nx1 != null) {
			apply(ctx.getData(), nx1);
			return true;
		} else {
			return transformNiximUser(ctx.getConstantPool(), null, registry);
		}
	}

	public void apply(ConstantData data, NiximData nx) throws NiximException {
		if (!data.name.equals(nx.target)) throw new NiximException("期待目标为"+nx.target+"而不是"+data.name);

		if (CLASS_DUMP) dump("nixim_in", data);
		while (nx != null) {
			data.unparsed();
			applyLoop(data, nx);
			nx = nx.next;
		}
		data.unparsed();
		if (CLASS_DUMP) dump("nixim_out", data);
	}
	private void applyLoop(ConstantData data, NiximData nx) throws NiximException {
		//System.out.println("NiximClass " + data.name);

		// 添加接口
		List<String> itfs = nx.impls;
		for (int i = 0; i < itfs.size(); i++) data.addInterface(itfs.get(i));

		List<MethodNode> methods = data.methods;
		Pcd tmp = new Pcd();

		// region 检查 Shadow 兼容性
		if (!nx.preconditions.isEmpty()) {
			Pcd.REVERSE.set(true);
			MyHashSet<Pcd> pcdRev = new MyHashSet<>(nx.preconditions);

			tmp.mapOwner = data.name;
			try {
				verifyPcd(nx.self, pcdRev, tmp, data.fields);
				verifyPcd(nx.self, pcdRev, tmp, methods);
			} finally {
				Pcd.REVERSE.remove();
			}

			for (Pcd pcd : pcdRev) {
				if ((pcd.mode&Pcd.SHADOW) != 0 && pcd.mapOwner.equals(data.name)) {
					throw new NiximException("@Shadow的目标缺失\n" +
						"源: " + nx.self + "\n" +
						"对象" + nx.preconditions + "\n" +
						"目标方法: " + data.methods + "\n" +
						"目标字段: " + data.fields);
				}
			}
		}
		// endregion
		// region 实施 Inject
		if (!nx.inject.isEmpty()) {
			for (int i = 0; i < methods.size(); i++) {
				MethodNode mn = methods.get(i);

				tmp.name = mn.name();
				tmp.desc = mn.rawDesc();

				InjectState state = nx.inject.remove(tmp);
				if (state == null) continue;

				if ((state.flags&SKIP_STATIC_CHECK) == 0 && (state.method.modifier & ACC_STATIC) != (mn.modifier & ACC_STATIC)) {
					throw new NiximException(nx.self+"."+state.method+"无法覆盖"+data.name+"."+mn+": static不匹配");
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
			if (!nx.inject.isEmpty()) {
				outer:
				for (Iterator<InjectState> itr = nx.inject.values().iterator(); itr.hasNext(); ) {
					InjectState state = itr.next();
					while (state != null) {
						if ((state.flags & Inject.OPTIONAL) == 0) {
							continue outer;
						}
						state = state.next;
					}
					itr.remove();
				}

				if (!nx.inject.isEmpty())
					throw new NiximException("以下Inject方法没有在目标找到, 源: " + nx.self + ": " + nx.inject.keySet() + ", 目标的方法: " + data.methods);
			}
			// endregion
		}
		// endregion


		if (nx.lambda != null && !nx.lambda.isEmpty()) {
			BootstrapMethods selfBSM = data.parsedAttr(data.cp,Attribute.BootstrapMethods);
			if (selfBSM == null) data.putAttr(selfBSM = new BootstrapMethods());

			for (Map.Entry<BootstrapMethods.Item, List<Desc>> entry : nx.lambda.entrySet()) {
				int newId = selfBSM.methods.size();
				selfBSM.methods.add(entry.getKey());

				List<Desc> info = entry.getValue();
				for (int i = 0; i < info.size(); i++) {
					info.get(i).flags = (char) newId;
				}
			}
		}


		for (CNode node : nx.copied) {
			if (node.rawDesc().startsWith("(")) methods.add((MethodNode) node);
			else data.fields.add((FieldNode) node);
		}


		if (nx.copyInit != null) {
			XAttrCode injectClInit = nx.copyInit.parsedAttr(null, Attribute.Code);

			XAttrCode code;
			XInsnNodeView.InsnMod clinit;
			int last;
			int clinit_id = data.getMethod("<clinit>");
			if (clinit_id >= 0) {
				code = data.methods.get(clinit_id).parsedAttr(data.cp, Attribute.Code);
				XInsnList insn = code.instructions;
				last = insn.getPcMap().last();
				insn.replaceRange(last, insn.bci(), injectClInit.instructions, XInsnList.REP_CLONE);
			} else {
				MethodNode mn = new MethodNode(ACC_PUBLIC | ACC_STATIC, data.name, "<clinit>", "()V");
				mn.putAttr(code = new XAttrCode());
				methods.add(mn);
				last = 0;

				code.instructions = injectClInit.instructions.copy();
			}

			copyLine(injectClInit, code, last);
		}


		for (int i = 0; i < data.methods.size(); i++) {
			XAttrCode m = data.methods.get(i).parsedAttr(null, Attribute.Code);
			if (m != null) removeLVT(m);
		}

		assert data.verify();
	}
	private static void verifyPcd(String self, MyHashSet<Pcd> pcds, Pcd tmp, List<? extends CNode> nodes) throws NiximException {
		int size = nodes.size();
		for (int i = 0; i < size; i++) {
			CNode node = nodes.get(i);
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
						throw new NiximException(self+'.'+pcd.name+" Shadow => "+pcd.mapOwner+'.'+pcd.mapName+" static/final不匹配");
					}
				} else if ((pcd.mode & Pcd.COPY) != 0) {
					throw new NiximException(self+'.'+pcd.name+" Copy => "+pcd.mapOwner+'.'+pcd.mapName+" 已存在");
				}
			}
		}
	}
	private static MethodNode doInject(InjectState s, ConstantData data, MethodNode input) throws NiximException {
		XAttrCode nxCode = (XAttrCode) s.method.attrByName("Code");
		s.method.owner = data.name;

		if (s.mapName.equals("<init>") && !s.at.equals("TAIL")) {
			Desc iin = nxCode.instructions.getNodeAt(s.initBci).desc();
			st:
			if (iin.name.startsWith(SPEC_M_CONSTRUCTOR_THIS)) {
				if (iin.param.equals(s.method.rawDesc())) throw new NiximException("this()循环调用!");
				iin.owner = data.name;
				for (int i = 0; i < data.methods.size(); i++) {
					MethodNode mn = data.methods.get(i);
					if (mn.rawDesc().equals(iin.param)) break st;
				}
				throw new NiximException("使用的this()构造器不存在");
			} else {
				// 无法检查上级构造器是否存在, 但是Object还是可以查一下
				iin.owner = data.parent;
				if (data.parent.equals("java/lang/Object") && !"()V".equals(iin.param)) {
					throw new NiximException("覆盖的构造器中的上级调用不存在(Object parent)\n" +
						"你也许忘了: $$CONSTRUCTOR是【目标】中的super()");
				}
			}

			iin.name = "<init>";
		}

		XAttrCode mnCode = input.parsedAttr(data.cp, Attribute.Code);
		switch (s.at) {
			case "REMOVE": return null;
			case "REPLACE": default: return s.method;
			case "HEAD": {
				Label endMy = null;

				block1:
				if (s.initBci >= 0) {
					for (XInsnNodeView node : mnCode.instructions) {
						Desc d = node.descOrNull();
						if (d != null && d.owner != null) {
							if (d.name.equals("<init>") && (d.owner.equals(data.parent) || d.owner.equals(data.name))) {
								endMy = node.end1();
								break block1;
							}
						}
					}
					throw new NiximException(data.name+" 存在错误: 无法找到替换构造器中<init>的调用");
				} else {
					endMy = new Label(0);
				}

				XInsnList replace = new XInsnList();

				XInsnList out = nxCode.instructions.copy();
				List<Label> jumps = s.headJump();
				// noinspection all
				if (endMy.getValue() == 0 && jumps.size() == 2 && jumps.get(1).getValue() == out.bci()) {
					out.replaceRange(jumps.get(0), jumps.get(1), replace, XInsnList.REP_SHARED_NOUPDATE);
				} else {
					replace.jump(mnCode.instructions.labelAt(endMy));

					for (int i = jumps.size()-1; i >= 0; i--) {
						Label end = jumps.get(i--);
						Label start = jumps.get(i--);
						out.replaceRange(start, end, replace, XInsnList.REP_SHARED_NOUPDATE);
					}
				}

				mnCode.instructions.replaceRange(new Label(0),endMy,out,XInsnList.REP_SHARED);

				mnCode.recomputeFrames(XAttrCode.COMPUTE_FRAMES, input);
				mnCode.stackSize = (char) Math.max(mnCode.stackSize, nxCode.stackSize);
				mnCode.localSize = (char) Math.max(mnCode.localSize, nxCode.localSize);

				copyLine(nxCode, mnCode, 0);
			}
			return input;
			case "INVOKE": {
				Desc m = Desc.fromJavapLike(s.extra.getString("matcher"));
				MyBitSet occurrences = s.getOccurrences();
				int ordinal = 0;

				List<XInsnNodeView.InsnMod> rpls = new SimpleList<>();
				int isStatic = (s.method.modifier & ACC_STATIC);
				int used = 0;
				for (XInsnList.NodeIterator itr = mnCode.instructions.iterator(); itr.hasNext(); ) {
					XInsnNodeView node = itr.next();
					Desc d = node.descOrNull();
					if (d != null && d.owner != null) {
						if ((m.owner.isEmpty() || m.owner.equals(d.owner)) &&
							d.name.equals(m.name) && d.param.equals(m.param)) {

							if (occurrences != null && !occurrences.contains(ordinal++)) continue;

							String desc;
							if ((node.opcode() == INVOKESTATIC) == isStatic > 0) {
								// 相同
								desc = d.param;
							} else if (node.opcode() != INVOKESTATIC) {
								// virtual -> static
								desc = "(L"+d.owner+';'+d.param.substring(1);
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
								List<Type> par2 = TypeHelper.parseMethod(desc);
								par2.remove(par2.size()-1);
								if (par1.size()-1 == par2.size() && data.name.equals(par1.get(par1.size()-1).owner)) {
									if ((input.modifier&ACC_STATIC) != 0) throw new NiximException("@InvokeRedirect.ContextInject 期待非静态方法: "+input.name()+"."+input.rawDesc());
									// context_inject

									itr = mnCode.instructions.since(node.bci());

									XInsnList list = new XInsnList();
									list.one(ALOAD_0);
									node.insertBefore(list, XInsnList.REP_CLONE);

									break block;
								}

								throw new NiximException("@InvokeRedirect "+m+" 参数不匹配: except "+desc+" , got "+s.method.rawDesc());
							}

							d.owner = data.name;
							d.name = s.method.name();
							d.param = s.method.rawDesc();

							// TODO 可能无法和aload_0一起使用
							if (node.opcode() == INVOKEINTERFACE) {
								XInsnNodeView.InsnMod rpl = node.replace();
								rpl.list.invoke(isStatic > 0 ? INVOKESTATIC : INVOKEVIRTUAL, d.owner, d.name, d.param);
								rpls.add(rpl);
							}

							used++;
						}
					}
				}

				if (used < (occurrences == null ? 1 : occurrences.size()) && (s.flags & Inject.OPTIONAL) == 0)
					throw new NiximException("@InvokeRedirect "+m+" 没有全部匹配("+used+")");

				for (int i = rpls.size() - 1; i >= 0; i--) rpls.get(i).commit();
			}
			return input;
			case "LDC": {
				int type = s.extra.getInt("matchType");
				String find = s.extra.getString("matchValue");

				String replace = s.extra.getString("replaceValue", "");
				XInsnList replaceTo = new XInsnList();
				if (!replace.isEmpty()) {
					switch (type) {
						case Constant.INT: replaceTo.ldc(Integer.parseInt(replace)); break;
						case Constant.LONG: replaceTo.ldc(Long.parseLong(replace)); break;
						case Constant.DOUBLE: replaceTo.ldc(Double.parseDouble(replace)); break;
						case Constant.FLOAT: replaceTo.ldc(Float.parseFloat(replace)); break;
						case Constant.CLASS: replaceTo.ldc(new CstClass(replace)); break;
						case Constant.STRING: replaceTo.ldc(replace); break;
						default: throw new NiximException("OverwriteConstant的matchType必须是Constant中的有效类别ID");
					}
				} else {
					if ((s.method.modifier & ACC_STATIC) == 0) {
						replaceTo.one(ALOAD_0);
						replaceTo.invokeV(data.name, s.method.name(), s.method.rawDesc());
					} else {
						replaceTo.invokeS(data.name, s.method.name(), s.method.rawDesc());
					}
				}

				MyBitSet occurrences = s.getOccurrences();
				CInt ordinal = new CInt();

				int used = 0;

				for (XInsnNodeView node : mnCode.instructions) {
					switch (node.opcode()) {
						case ICONST_M1, ICONST_0:
						case ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5:
							if (type == Constant.INT && doLdcMatchBytecode(node, ICONST_0, find, occurrences, ordinal)) {
								node.replace(replaceTo, XInsnList.REP_SHARED);
								used++;
							}
						break;
						case LCONST_0, LCONST_1:
							if (type == Constant.LONG && doLdcMatchBytecode(node, LCONST_0, find, occurrences, ordinal)) {
								node.replace(replaceTo, XInsnList.REP_SHARED);
								used++;
							}
						break;
						case FCONST_0, FCONST_1, FCONST_2:
							if (type == Constant.FLOAT && doLdcMatchBytecode(node, FCONST_0, find, occurrences, ordinal)) {
								node.replace(replaceTo, XInsnList.REP_SHARED);
								used++;
							}
						break;
						case DCONST_0, DCONST_1:
							if (type == Constant.DOUBLE && doLdcMatchBytecode(node, DCONST_0, find, occurrences, ordinal)) {
								node.replace(replaceTo, XInsnList.REP_SHARED);
								used++;
							}
						break;
						case BIPUSH, SIPUSH:
							if (type == Constant.INT && find.equals(String.valueOf(node.getNumberExact())) &&
								(occurrences == null || occurrences.contains(++ordinal.value))) {
								node.replace(replaceTo, XInsnList.REP_SHARED);
								used++;
							}
						break;
						case LDC, LDC_W, LDC2_W:
							if (type == node.constant().type() && find.equals(node.constant().getEasyCompareValue()) &&
								(occurrences == null || occurrences.contains(++ordinal.value))) {
								node.replace(replaceTo, XInsnList.REP_SHARED);
								used++;
							}
						break;
					}
				}

				if (used < (occurrences == null ? 1 : occurrences.size()) && (s.flags & Inject.OPTIONAL) == 0)
					throw new NiximException("@OverwriteConstant "+find+" 没有全部匹配("+used+")");
			}
			return input;
			case "TAIL": {
				if (s.initBci >= 0) throw new NiximException(SPEC_M_CONSTRUCTOR+"不能在TAIL使用");

				XInsnList insn = mnCode.instructions;
				XInsnList hookInsn = nxCode.instructions.copy();
				int paramSize = TypeHelper.paramSize(input.rawDesc());

				// 寻找空位放返回值和变量
				MyBitSet usedSlots = null;
				int retValId = -1;

				XInsnList tmpList = new XInsnList();

				Type ret = input.returnType();
				int returnHookType;
				if (ret.type == Type.VOID) {
					if (!s.retVal().isEmpty()) throw new NiximException("VOID返回值不能使用"+SPEC_M_RETVAL);
					returnHookType = 0x100; // JUMP
				} else if (!s.retVal().isEmpty()) {
					returnHookType = ret.shiftedOpcode(ISTORE);
					if (s.retVal().size() == 1 && s.retVal().get(0).bci() == 0) {
						returnHookType |= 0x100; // JUMP
					} else {
						returnHookType |= 0x200; // STORE_AND_JUMP

						usedSlots = collectUsedSlots(insn, paramSize);
						retValId = getVarId(usedSlots, ret.nativeName());
						tmpList.varLoad(ret, retValId);
					}
				} else {
					returnHookType = 0x000; // POP_JUMP
				}

				for (XInsnNodeView node : hookInsn) {
					Desc desc = node.descOrNull();
					if (desc != null && desc.name.startsWith(SPEC_M_RETVAL)) {
						node.replace(tmpList, XInsnList.REP_CLONE);
					}
				}

				int begin;
				parBR:
				if (s.assignId != null) {
					Int2IntMap overwriteCheck = new Int2IntMap();

					for (ToIntMap.Entry<XInsnNodeView> entry : s.assignId.selfEntrySet()) {
						overwriteCheck.put(entry.v, -1);
						String name = entry.k.opName();
						if (name.charAt(0) == 'D' || name.charAt(0) == 'L')
							overwriteCheck.put(entry.v+1, -2);
					}

					// 备份参数，计算tail用到的参数id(已完成)，若修改过则暂存到新的变量中再恢复
					tmpList.clear();
					for (XInsnNodeView node : insn) {
						String name = node.opName();
						int vid = node.getVarId();
						if (vid >= 0 && name.startsWith("Store", 1)) {
							Int2IntMap.Entry entry = overwriteCheck.getEntry(vid);
							if (entry != null && entry.v == -1) {
								if (usedSlots == null) usedSlots = collectUsedSlots(insn, paramSize);
								int slot = getVarId(usedSlots, name);

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

					insn.replaceRange(0,0,tmpList,XInsnList.REP_SHARED);

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

					hookInsn.replaceRange(0, 0, tmpList, XInsnList.REP_SHARED);
				} else {
					begin = 0;
				}

				Label hookBegin = hookInsn.labelAt(0);
				int end = insn.bci();
				insn.replaceRange(end, end, hookInsn, XInsnList.REP_SHARED);

				for (XInsnList.NodeIterator it = insn.since(begin); it.hasNext(); ) {
					XInsnNodeView node = it.next();
					if (node.bci() >= end) break;

					if (node.opName().endsWith("Return")) {
						tmpList.clear();

						switch (returnHookType>>>8) {
							case 0: tmpList.one(POP); break; // 放弃返回值
							case 1: break; // 形如int v=$$VALUE_I(),并且就在方法开头  或者  是VOID 直接跳
							case 2: tmpList.vars((byte) returnHookType, retValId); break; // 储存至变量
						}

						if (node.bci()+node.length() < end) {
							tmpList.jump(hookBegin);
							end += tmpList.bci() - node.length();
						}

						node.replace(tmpList, XInsnList.REP_SHARED_NOUPDATE);
						it = insn.since(node.bci());
					}
				}

				mnCode.recomputeFrames(XAttrCode.COMPUTE_FRAMES, input);
				mnCode.stackSize = (char) Math.max(mnCode.stackSize, nxCode.stackSize);
				mnCode.localSize = (char) Math.max(mnCode.localSize, nxCode.localSize);
				if ((returnHookType & 0x100) != 0) mnCode.stackSize = (char) Math.max(mnCode.stackSize, 1);
				if (usedSlots != null) mnCode.localSize = (char) Math.max(mnCode.localSize, usedSlots.last());

				copyLine(nxCode, mnCode, end+begin);
			}
			return input;
			case "OLD_SUPER_INJECT": {
				data.methods.add(s.method);
				String mapName = "nx^SIJ@@"+randomId((System.nanoTime() << 32) | s.mapName.hashCode());
				input.name(mapName);
				for (Map.Entry<Label, Object> entry : nxCode.instructions.nodeData()) {
					if (entry.getValue().getClass() == Desc.class) {
						Desc d = (Desc) entry.getValue();
						if ((data.name.equals(d.owner) || d.owner.contains("SIJ")) && s.mapName.equals(d.name) && s.method.rawDesc().equals(d.param)) {
							d.owner = data.name;
							d.name = mapName;
						}
					}
				}
			}
			return input;
			case "MIDDLE":
				XInsnList toFind = mnCode.instructions;
				XInsnList matcher = s.matcher;
				InsnHelper ctx = new InsnHelper() {
					int iPos = -2;

					@Override
					public boolean isNodeSimilar(XInsnNodeView haystack, XInsnNodeView needle) {
						if (needle.opcode() == INVOKESTATIC) {
							Desc d = needle.desc();
							if (d.name.startsWith(SPEC_M_ANYVAR)) {
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

				XInsnNodeView matchFirst = matcher.getNodeAt(0);
				for (XInsnNodeView node : toFind) {
					ctx.reset();

					findFirst:
					if (ctx.isNodeSimilar(node, matchFirst)) {
						XInsnList.NodeIterator
							itrA = toFind.since(node.bci()+node.length()),
							itrB = matcher.since(matchFirst.length());
						while (itrA.hasNext()&&itrB.hasNext()) {
							XInsnNodeView A = itrA.next(), B = itrB.next();
							if (!ctx.isNodeSimilar(A, B)) {
								System.out.println("second failed on "+A+" and "+B);
								break findFirst;
							}
						}

						System.out.println("successfully match sequence "+toFind.copySlice((Label) node.pos(), itrA.unsharedPos()));
						// found
						XInsnList toInj = nxCode.instructions.copy();
						ctx.mapVarId(toInj);

						toFind.replaceRange(toFind.labelAt(node.pos()), itrA.unsharedPos(), toInj, XInsnList.REP_SHARED);

						mnCode.recomputeFrames(XAttrCode.COMPUTE_FRAMES, input);
						mnCode.stackSize = (char) Math.max(mnCode.stackSize, nxCode.stackSize);
						mnCode.localSize = (char) Math.max(mnCode.localSize, nxCode.localSize);

						copyLine(nxCode, mnCode, node.bci());
						return input;
					}
				}
				throw new NiximException("MIDDLE没有匹配成功: 目标方法: "+mnCode+"\n匹配方法: "+matcher);
		}
	}
	private static MyBitSet collectUsedSlots(XInsnList insn, int paramSize) {
		MyBitSet usedSlots = new MyBitSet();
		usedSlots.fill(paramSize);
		for (XInsnNodeView node : insn) {
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
	private static int getVarId(MyBitSet usedSlots, String name) {
		int newSlot = 0;
		int len = name.charAt(0) == 'D' || name.charAt(0) == 'L' ? 2 : 1;
		while (!usedSlots.allFalse(newSlot, newSlot+len)) {
			newSlot++;
		}
		usedSlots.addRange(newSlot, newSlot+len);
		return newSlot;
	}
	private static boolean doLdcMatchBytecode(XInsnNodeView node, byte base, String find, MyBitSet occurrences, CInt ordinal) {
		int value = node.opcode() - base;
		if (!find.equals(String.valueOf(value))) return false;
		return occurrences == null || occurrences.contains(++ordinal.value);
	}
	private static void copyLine(XAttrCode from, XAttrCode to, int pcOff) {
		LineNumberTable lnFr = (LineNumberTable) from.attrByName("LineNumberTable");
		LineNumberTable lnTo = (LineNumberTable) to.attrByName("LineNumberTable");

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

		MyBitSet pcMap = to.instructions.getPcMap();
		for (LineNumberTable.Item item : lnFr.list) {
			int value = item.pos.getValue();
			if (pcMap.contains(value+pcOff)) {
				// TODO copy lines better (consider hole)
				list.add(new LineNumberTable.Item(to.instructions.labelAt(value+pcOff), item.getLine()));
			}
		}

		lnTo.sort();
	}
	private static void removeLVT(XAttrCode code) {
		AttributeList list = code.attributesNullable();
		if (list != null) {
			list.removeByName("LocalVariableTable");
			list.removeByName("LocalVariableTypeTable");
		}
	}


	private static Map<String, Annotation> getAnnotations(Attributed node, ConstantData data) {
		Annotations attr = node.parsedAttr(data.cp, Attribute.ClAnnotations);
		if (attr == null || attr.isEmpty()) return Collections.emptyMap();
		MyHashMap<String, Annotation> map = new MyHashMap<>(attr.annotations.size());
		List<Annotation> annotations = attr.annotations;
		for (int i = annotations.size()-1; i >= 0; i--) {
			Annotation a = annotations.get(i);
			map.put(a.type(), a);
			if (a.type().startsWith("roj/asmx/nixim/")) annotations.remove(i);
		}
		return map;
	}
	private static String randomId(long v) {
		CharList sb = IOUtil.getSharedCharBuf();
		v &= Long.MAX_VALUE;
		while (v != 0) {
			sb.append((char)TextUtil.digits[(int) (v%62)]);
			v /= 62;
		}
		return sb.toString();
	}
	private static String unifyClassName(String t) { return t.replace('.', '/'); }

	private static class MyCodeVisitor extends CodeVisitor {
		private final ConstantData data;
		private final MyHashSet<Desc> inaccessible;
		private final NiximData nx;
		private final SimpleList<MethodNode> autoCopy;

		private final Desc tmp = ClassUtil.getInstance().sharedDC;
		private final Pcd tmp2 = new Pcd();
		private int initFlag;
		private boolean isConstructor;

		InjectState state;
		MyHashSet<MethodNode> copied = new MyHashSet<>(Hasher.identity());

		private MethodNode mn;

		public MyCodeVisitor(ConstantData data, MyHashSet<Desc> inaccessible, NiximData nx, SimpleList<MethodNode> autoCopy) {
			this.data = data;
			this.inaccessible = inaccessible;
			this.nx = nx;
			this.autoCopy = autoCopy;
		}

		public void MyVisit(MethodNode node) {
			this.mn = node;
			if (node.name().equals("<init>")) {
				initFlag = 1;
			} else if (node.name().equals("<clinit>")) {
				initFlag = 2;
			} else {
				initFlag = 0;
			}
			isConstructor = false;

			Attribute code = node.attrByName("Code");
			if (code != null) visit(data.cp, Parser.reader(code));
		}

		@Override
		public void invoke(byte code, CstRef method) {
			checkAccess(method);

			String name = method.descName();
			if (name.startsWith(SPEC_M_CONSTRUCTOR)) {
				if (isConstructor) throw new IllegalStateException("构造器("+SPEC_M_CONSTRUCTOR+")只能调用一次");
				isConstructor = true;
				if (state != null) {
					state.initBci = bci;
				}
			}

			if (state != null && (code == INVOKESTATIC || code == INVOKESPECIAL)) {
				if (name.equals(state.mapName) && method.descType().equals(state.method.rawDesc())) {
					state.flags |= 65536;
				}
			}
		}

		@Override
		public void field(byte code, CstRefField field) {
			checkAccess(field);

			if (initFlag != 2) {
				if (Opcodes.showOpcode(code).startsWith("Get")) return;

				tmp2.name = field.descName();
				tmp2.desc = field.descType();
				Pcd pcd = nx.preconditions.find(tmp2);
				if (pcd != tmp2) {
					if ((pcd.mode& ACC_STATIC) == 0 && initFlag == 1) return;
					if ((pcd.mode& ACC_FINAL) != 0) {
						Helpers.athrow(new NiximException("不能修改final或为实际上final的" + field + "\n" +
							"源: "+data.name+"\n" +
							"函数: "+mn));
					}
				}
			}
		}

		private void checkAccess(CstRef ref) {
			if (ref.className().equals(data.name) && inaccessible.contains(tmp.read(ref))) {
				if (!autoCopy(tmp))
					Helpers.athrow(new NiximException("请使用@Copy注解以在运行时访问"+tmp));
			}
		}

		private boolean autoCopy(Desc d) {
			for (int i = 0; i < autoCopy.size(); i++) {
				MethodNode node = autoCopy.get(i);
				if (node.descMatch(d)) {
					copied.add(node);
					return true;
				}
			}
			return false;
		}
	}

	protected boolean shouldApply(String annotation, Attributed node, List<AnnValString> args) { throw new UnsupportedOperationException(); }
	public Map<String, NiximData> registry() { return registry; }
	protected String mapName(String owner, String newOwner, String name, CNode node) { return name; }
}