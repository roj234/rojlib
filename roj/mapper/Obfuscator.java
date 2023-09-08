package roj.mapper;

import roj.asm.misc.ReflectClass;
import roj.asm.tree.*;
import roj.asm.util.AccessFlag;
import roj.asm.util.Context;
import roj.collect.FindMap;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.mapper.util.Desc;
import roj.mapper.util.SubImpl;
import roj.text.TextUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * class混淆器
 *
 * @author Roj233
 * @since 2021/7/18 18:33
 */
public abstract class Obfuscator {
	public static final String TERMINATE_THIS_CLASS = new String();

	public static final int ADD_SYNTHETIC = 1, AUTO_ACCESSIBLE = 2, REMOVE_SYNTHETIC = 4;

	Mapper m1;

	protected int flags;
	private Map<String, IClass> named;

	public Obfuscator() {
		m1 = new Mapper(true);
		m1.flag = Mapper.FLAG_FIX_INHERIT | Mapper.FLAG_FIX_SUBIMPL;
	}

	public void clear() {
		m1.clear();
		if (named != null) named.clear();
	}

	public void loadLibraries(List<?> libraries) {
		m1.loadLibraries(libraries);
	}

	public void reset() {
		Mapper t = m1;
		t.classMap.clear();
		t.methodMap.clear();
		t.fieldMap.clear();
		named.clear();
	}

	public void obfuscate(List<Context> arr) {
		Mapper m = m1;

		if (named == null) named = new MyHashMap<>(arr.size());
		for (int i = 0; i < arr.size(); i++) {
			ConstantData data = arr.get(i).getData();
			named.put(data.name, data);
		}

		Context cur = null;
		try {
			m.initSelf(arr.size());
			for (int i = 0; i < arr.size(); i++) m.S1_parse(cur = arr.get(i));

			m.initSelfSuperMap();
			for (int i = 0; i < arr.size(); i++) prepare(cur = arr.get(i));

			// 删去冲突项
			m.loadLibraries(Collections.singletonList(arr));

			// 反转字段映射
			MapUtil U = MapUtil.getInstance();
			MyHashSet<Desc> fMapReverse = new MyHashSet<>(m.fieldMap.size());
			for (Map.Entry<Desc, String> entry : m.fieldMap.entrySet()) {
				Desc desc = entry.getKey();
				Desc target = new Desc(desc.owner, entry.getValue(), desc.param, desc.flags);
				fMapReverse.add(target);
			}

			// 防止同名同参字段在继承链上出现, JVM也分辨不出
			Desc d = new Desc();
			List<Context> pending = arr;
			do {
				List<Context> next = new ArrayList<>();
				for (int i = 0; i < pending.size(); i++) {
					cur = pending.get(i);
					ConstantData data = cur.getData();
					List<String> parents = m.selfSupers.get(data.name);
					if (parents == null) continue;

					List<? extends FieldNode> fields = data.fields;
					for (int j = 0; j < fields.size(); j++) {
						RawField field = (RawField) fields.get(j);
						d.owner = data.name;
						d.name = field.name();
						d.param = field.rawDesc();

						// no map
						String name = m.fieldMap.get(d);
						if (null == name) continue;
						d.name = name;

						for (int k = 0; k < parents.size(); k++) {
							d.owner = parents.get(k);
							Desc d1 = fMapReverse.find(d);
							if (d1 == d) continue;

							// duplicate...
							if ((d1.flags & AccessFlag.PRIVATE) != 0) {
								// Uninheritable private field
								break;
							} else if ((d1.flags & (AccessFlag.PROTECTED | AccessFlag.PUBLIC)) == 0) {
								if (!MapUtil.arePackagesSame(d.owner, data.name)) {
									// Uninheritable package-private field
									break;
								}
							}

							d.owner = data.name;
							do {
								name = obfFieldName(data, d);
								// remove old
								fMapReverse.remove(d);
								if (name == null) {
									d.name = field.name();
									m.fieldMap.remove(d);
								} else {
									d.name = name;
									// add / check duplicate
									if (d != fMapReverse.intern(d)) {
										continue;
									}
									d = d.copy();

									d.name = field.name();
									// change mapping
									m.fieldMap.put(d, name);

									// push next
									if (!next.contains(cur)) next.add(cur);
								}
								break;
							} while (true);

							break;
						}
					}
				}
				pending = next;
			} while (!pending.isEmpty());

			FindMap<Desc, String> methodMap = m.getMethodMap();
			if (!methodMap.isEmpty()) {
				Set<SubImpl> subs = MapUtil.getInstance().findSubImplements(arr, m, named);

				for (SubImpl method : subs) {
					Desc desc = method.type.copy();

					for (Set<String> classList : method.owners) {
						if (classList.remove(null)) {
							for (String owner : classList) {
								desc.owner = owner;
								methodMap.remove(desc);
							}
							continue;
						}

						String mapName = null, mapClass = null;
						for (String owner : classList) {
							desc.owner = owner;

							String name = methodMap.get(desc);
							if (name != null) {
								mapClass = owner;
								mapName = name;
								break;
							}
						}

						for (String owner : classList) {
							if (owner.equals(mapClass)) continue;
							desc.owner = owner;

							if (!mapName.equals(methodMap.put(desc, mapName))) {
								List<? extends MoFNode> methods1 = named.get(desc.owner).methods();
								for (int i = 0; i < methods1.size(); i++) {
									MoFNode mn = methods1.get(i);
									if (desc.param.equals(mn.rawDesc()) && desc.name.equals(mn.name())) {
										desc.flags = mn.modifier();
										break;
									}
								}

								desc = desc.copy();
							}
						}
					}
				}
			}

			for (int i = 0; i < arr.size(); i++) m.S2_mapSelf(cur = arr.get(i), false);
			for (int i = 0; i < arr.size(); i++) m.S3_mapConstant(cur = arr.get(i));

			beforeMapCode(arr);

			for (int i = 0; i < arr.size(); i++) m.S4_mapClassName(cur = arr.get(i));
			for (int i = 0; i < arr.size(); i++) (cur = arr.get(i)).compress();

			afterMapCode(arr);
		} catch (Throwable e) {
			throw new RuntimeException("At parsing " + cur, e);
		}
	}

	private void prepare(Context c) {
		ConstantData data = c.getData();
		data.normalize();

		String dest = obfClass(data);
		if (dest == TERMINATE_THIS_CLASS) return;

		Mapper m = m1;
		if (dest != null && m.classMap.putIfAbsent(data.name, dest) != null) {
			System.out.println("重复的class name " + data.name);
		}

		prepareInheritCheck(data.name);
		Desc desc = new Desc(data.name, "", "");
		List<? extends MethodNode> methods = data.methods;
		for (int i = 0; i < methods.size(); i++) {
			RawMethod method = (RawMethod) methods.get(i);
			int acc = method.access;
			if ((flags & ADD_SYNTHETIC) != 0) {
				acc |= AccessFlag.SYNTHETIC;
			} else if ((flags & REMOVE_SYNTHETIC) != 0) {
				acc &= ~AccessFlag.SYNTHETIC;
			}
			method.access = (char) acc;

			if ((desc.name = method.name.str()).charAt(0) == '<') continue; // clinit, init
			desc.param = method.type.str();
			if (0 == (acc & (AccessFlag.STATIC | AccessFlag.PRIVATE))) {
				if (isInherited(desc)) continue;
			}
			desc.flags = (char) acc;

			String ms = obfMethodName(data, desc);
			if (ms != null) {
				m.methodMap.putIfAbsent(desc, ms);
				desc = new Desc(data.name, "", "");
			}
		}

		List<? extends FieldNode> fields = data.fields;
		for (int i = 0; i < fields.size(); i++) {
			RawField field = (RawField) fields.get(i);
			int acc = field.access;
			if ((flags & ADD_SYNTHETIC) != 0) {
				acc |= AccessFlag.SYNTHETIC;
			} else if ((flags & REMOVE_SYNTHETIC) != 0) {
				acc &= ~AccessFlag.SYNTHETIC;
			}
			field.access = (char) acc;

			desc.name = field.name.str();
			desc.param = field.type.str();
			desc.flags = (char) acc;

			String fs = obfFieldName(data, desc);
			if (fs != null) {
				m.fieldMap.putIfAbsent(desc, fs);
				desc = new Desc(data.name, "", "");
			}
		}
	}

	// 好像不是必须的了？
	private final List<String> iCheckTmp = new ArrayList<>();
	private void prepareInheritCheck(String owner) {
		List<String> tmp = iCheckTmp;
		tmp.clear();

		Map<String, List<String>> supers = m1.selfSupers;
		List<String> parents = supers.get(owner);
		if (parents != null) {
			for (int i = 0; i < parents.size(); i++) {
				String parent = parents.get(i);
				if (!supers.containsKey(parent) && !named.containsKey(parent)) tmp.add(parent);
			}
		}
	}
	private boolean isInherited(Desc k) { return iCheckTmp.isEmpty() ? MapUtil.checkObjectInherit(k) : MapUtil.getInstance().isInherited(k, iCheckTmp, true); }

	public void writeObfuscationMap(File file) throws IOException {
		m1.saveMap(file);
	}

	public void setFlags(int flags) {
		this.flags = flags;
	}

	public void dumpMissingClasses() {
		List<String> notFoundClasses = new SimpleList<>();
		for (Map.Entry<String, ReflectClass> s : MapUtil.getInstance().classInfo.entrySet()) {
			if (s.getValue() == MapUtil.FAILED) notFoundClasses.add(s.getKey());
		}

		if (!notFoundClasses.isEmpty()) {
			System.out.print(TextUtil.deepToString(notFoundClasses));
			System.out.println(notFoundClasses.size() + "个类没有找到");
			System.out.println("如果你没有在libraries中给出这些类, 则会影响混淆水平");
			System.out.println("(我的意思是即使你给了,这里也可能会提示)");
		}
	}

	protected void beforeMapCode(List<Context> arr) {}
	protected void afterMapCode(List<Context> arr) {}

	public abstract String obfClass(IClass cls);
	public abstract String obfMethodName(IClass cls, Desc entry);
	public abstract String obfFieldName(IClass cls, Desc entry);
}
