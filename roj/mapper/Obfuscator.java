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
	public static final String TREMINATE_THIS_CLASS = new String();

	public static final int ADD_SYNTHETIC = 1, ADD_PUBLIC = 2, REMOVE_SYNTHETIC = 4;

	ConstMapper m1;
	CodeMapper m2;

	protected int flags;
	private Map<String, IClass> named;

	public Obfuscator() {
		m1 = new ConstMapper(true);
		m1.flag = ConstMapper.TRIM_DUPLICATE | ConstMapper.FLAG_CHECK_SUB_IMPL;
		m2 = new CodeMapper(m1);
	}

	public void clear() {
		m1.clear();
		if (named != null) named.clear();
	}

	public void loadLibraries(List<?> libraries) {
		m1.loadLibraries(libraries);
	}

	public void reset() {
		ConstMapper t = m1;
		t.classMap.clear();
		t.methodMap.clear();
		t.fieldMap.clear();
		named.clear();
	}

	public void obfuscate(List<Context> arr) {
		ConstMapper t = m1;

		if (named == null) named = new MyHashMap<>(arr.size());
		for (int i = 0; i < arr.size(); i++) {
			ConstantData data = arr.get(i).getData();
			named.put(data.name, data);
		}

		Context cur = null;
		try {
			t.initSelf(arr.size());

			for (int i = 0; i < arr.size(); i++) {
				t.S1_parse(cur = arr.get(i));
			}

			t.initSelfSuperMap();

			for (int i = 0; i < arr.size(); i++) {
				prepare(cur = arr.get(i));
			}

			// 删去冲突项
			t.loadLibraries(Collections.singletonList(arr));

			// 反转字段映射
			MapUtil U = MapUtil.getInstance();
			MyHashSet<Desc> fMapReverse = new MyHashSet<>(t.fieldMap.size());
			for (Map.Entry<Desc, String> entry : t.fieldMap.entrySet()) {
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
					List<String> parents = t.selfSupers.get(data.name);
					if (parents == null) continue;

					List<? extends FieldNode> fields = data.fields;
					for (int j = 0; j < fields.size(); j++) {
						RawField field = (RawField) fields.get(j);
						d.owner = data.name;
						d.name = field.name();
						d.param = field.rawDesc();

						// no map
						String name = t.fieldMap.get(d);
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
									t.fieldMap.remove(d);
								} else {
									d.name = name;
									// add / check duplicate
									if (d != fMapReverse.intern(d)) {
										continue;
									}
									d = d.copy();

									d.name = field.name();
									// change mapping
									t.fieldMap.put(d, name);

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

			FindMap<Desc, String> methodMap = t.getMethodMap();
			if (!methodMap.isEmpty()) {
				MyHashSet<SubImpl> subs = MapUtil.getInstance().gatherSubImplements(arr, t, named);
				for (SubImpl impl : subs) {
					Desc finder = impl.type;
					Iterator<String> itr = impl.owners.iterator();

					// 不是外面的方法
					if (!impl.immutable) {
						String firstName;
						do {
							finder.owner = itr.next(); // findFirst
							firstName = methodMap.get(finder);
						} while (itr.hasNext() && firstName == null);

						if (firstName != null) {
							itr = impl.owners.iterator();
							while (itr.hasNext()) {
								finder.owner = itr.next();
								methodMap.replace(finder, firstName);
							}
						}
					} else {
						while (itr.hasNext()) {
							finder.owner = itr.next();
							methodMap.remove(finder);
						}
					}
				}
			}

			for (int i = 0; i < arr.size(); i++) {
				t.S2_mapSelf(cur = arr.get(i));
			}

			for (int i = 0; i < arr.size(); i++) {
				t.S3_mapConstant(cur = arr.get(i));
			}

			beforeMapCode(arr);

			CodeMapper cm = m2;

			for (int i = 0; i < arr.size(); i++) {
				cm.processOne(cur = arr.get(i));
			}

			for (int i = 0; i < arr.size(); i++) {
				cur = arr.get(i);
				cur.compress();
			}

			afterMapCode(arr);
		} catch (Throwable e) {
			throw new RuntimeException("At parsing " + cur, e);
		}
	}

	protected void prepare(Context c) {
		ConstantData data = c.getData();
		data.normalize();

		String dest = obfClass(data);
		if (dest == TREMINATE_THIS_CLASS) return;

		ConstMapper t = this.m1;
		if (dest != null && t.classMap.putIfAbsent(data.name, dest) != null) {
			System.out.println("重复的class name " + data.name);
		}
		if ((flags & ADD_PUBLIC) != 0) {
			data.access |= AccessFlag.PUBLIC;
		}

		prepareInheritCheck(data.name);
		Desc desc = new Desc(data.name, "", "");
		List<? extends MethodNode> methods = data.methods;
		for (int i = 0; i < methods.size(); i++) {
			RawMethod method = (RawMethod) methods.get(i);
			int acc = method.accesses;
			if ((flags & ADD_SYNTHETIC) != 0) {
				acc |= AccessFlag.SYNTHETIC;
			} else if ((flags & REMOVE_SYNTHETIC) != 0) {
				acc &= ~AccessFlag.SYNTHETIC;
			}
			if ((flags & ADD_PUBLIC) != 0 && (acc & AccessFlag.PRIVATE) == 0) {
				acc &= ~AccessFlag.PROTECTED;
				acc |= AccessFlag.PUBLIC;
			}
			method.accesses = (char) acc;

			if ((desc.name = method.name.getString()).charAt(0) == '<') continue; // clinit, init
			desc.param = method.type.getString();
			if (0 == (acc & (AccessFlag.STATIC | AccessFlag.PRIVATE))) {
				if (isInherited(desc)) continue;
			}
			desc.flags = (char) acc;

			String ms = obfMethodName(data, desc);
			if (ms != null) {
				t.methodMap.putIfAbsent(desc, ms);
				desc = new Desc(data.name, "", "");
			}
		}

		List<? extends FieldNode> fields = data.fields;
		for (int i = 0; i < fields.size(); i++) {
			RawField field = (RawField) fields.get(i);
			int acc = field.accesses;
			if ((flags & ADD_SYNTHETIC) != 0) {
				acc |= AccessFlag.SYNTHETIC;
			} else if ((flags & REMOVE_SYNTHETIC) != 0) {
				acc &= ~AccessFlag.SYNTHETIC;
			}
			if ((flags & ADD_PUBLIC) != 0 && (acc & AccessFlag.PRIVATE) == 0) {
				acc &= ~AccessFlag.PROTECTED;
				acc |= AccessFlag.PUBLIC;
			}
			field.accesses = (char) acc;

			desc.name = field.name.getString();
			desc.param = field.type.getString();
			desc.flags = (char) acc;

			String fs = obfFieldName(data, desc);
			if (fs != null) {
				t.fieldMap.putIfAbsent(desc, fs);
				desc = new Desc(data.name, "", "");
			}
		}
	}

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

	private boolean isInherited(Desc k) {
		return iCheckTmp.isEmpty() ? MapUtil.checkObjectInherit(k) : MapUtil.getInstance().isInherited(k, iCheckTmp, true);
	}

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
