package roj.mapper;

import roj.collect.*;
import roj.io.IOUtil;
import roj.mapper.util.Desc;
import roj.mapper.util.MapperList;
import roj.math.MutableInt;
import roj.text.CharList;
import roj.text.LineReader;
import roj.text.TextUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Class Mapping (format: XSrg)
 *
 * @author Roj234
 * @since 2020/8/28 19:18
 */
public class Mapping {
	protected Flippable<String, String> classMap;
	protected FindMap<Desc, String> fieldMap, methodMap;
	protected TrieTree<String> packageMap;
	public boolean checkFieldType;

	public static Mapping fullCopy(Mapping m1) {
		Mapping m = new Mapping(m1.checkFieldType);
		m.classMap.putAll(m1.classMap);
		m.fieldMap.putAll(m1.fieldMap);
		m.methodMap.putAll(m1.methodMap);
		if (m1.packageMap != null) m.packageMap = new TrieTree<>(m1.packageMap);
		return m;
	}

	public Mapping() {
		this(false);
	}

	public Mapping(boolean checkFieldType) {
		this.checkFieldType = checkFieldType;
		this.classMap = new HashBiMap<>(1000);
		this.fieldMap = new MyHashMap<>(1000);
		this.methodMap = new MyHashMap<>(1000);
	}

	public Mapping(Mapping o) {
		this.classMap = o.classMap;
		this.fieldMap = o.fieldMap;
		this.methodMap = o.methodMap;
		this.packageMap = o.packageMap;
		this.checkFieldType = o.checkFieldType;
	}

	public final void loadMap(File path, boolean reverse) {
		try {
			loadMap(new LineReader(new FileInputStream(path), StandardCharsets.UTF_8), reverse);
		} catch (IOException e) {
			throw new RuntimeException("Unable to read mapping file", e);
		}
	}
	public final void loadMap(InputStream in, boolean reverse) {
		try {
			loadMap(new LineReader(in), reverse);
		} catch (IOException e) {
			throw new RuntimeException("Unable to read mapping file", e);
		}
	}
	@SuppressWarnings("fallthrough")
	public final void loadMap(LineReader slr, boolean reverse) {
		try {
			ArrayList<String> q = new ArrayList<>();
			String last0 = null, last1 = null;

			for (String s : slr) {
				int i = s.indexOf(':');
				String key = s.substring(0, i);

				q.clear();
				TextUtil.split(q, s.substring(i + 1), ' ');

				int id, id2;
				switch (key) {
					case "PK": // package
						if (!q.get(0).equals(q.get(1))) {
							if (packageMap == null) packageMap = new TrieTree<>();

							if (reverse) {
								packageMap.put(q.get(1), q.get(0));
							} else {
								packageMap.put(q.get(0), q.get(1));
							}
						}
						break;
					case "CL": // class
						if (q.get(1).equals("~")) q.set(1, q.get(0));

						if (reverse) {
							classMap.put(q.get(1), q.get(0));
						} else {
							classMap.put(q.get(0), q.get(1));
						}
						last0 = q.get(0);
						last1 = q.get(1);
						break;
					case "FD":
						id = q.get(0).lastIndexOf('/');
						id2 = q.get(1).lastIndexOf('/');

						if (reverse) {
							fieldMap.put(new Desc(q.get(1).substring(0, id2), q.get(1).substring(id2 + 1)), q.get(0).substring(id + 1));
						} else {
							fieldMap.put(new Desc(q.get(0).substring(0, id), q.get(0).substring(id + 1)), q.get(1).substring(id2 + 1));
						}
						break;
					case "MD":
						id = q.get(0).lastIndexOf('/');
						id2 = q.get(2).lastIndexOf('/');

						if (reverse) {
							methodMap.put(new Desc(q.get(2).substring(0, id2), q.get(2).substring(id2 + 1), q.get(3)), q.get(0).substring(id + 1));
						} else {
							methodMap.put(new Desc(q.get(0).substring(0, id), q.get(0).substring(id + 1), q.get(1)), q.get(2).substring(id2 + 1));
						}
						break;
					case "FL":
						// FL b c
						if (last0 == null) throw new IllegalArgumentException("last[0] == null at line " + slr.lineNumber());

						if (q.size() == 2) {
							if (checkFieldType) throw new IllegalArgumentException("FL(2) is not supported when checkFieldType=true");
							if (reverse) {
								fieldMap.put(new Desc(last1, q.get(1)), q.get(0));
							} else {
								fieldMap.put(new Desc(last0, q.get(0)), q.get(1));
							}
							break;
						}
					case "ML":
						if (last0 == null) throw new IllegalArgumentException("last[0] == null at line " + slr.lineNumber());

						FindMap<Desc, String> dst = key.equals("ML") ? methodMap : fieldMap;
						if (reverse) {
							dst.put(new Desc(last1, q.get(2), q.get(3).equals("~") ? q.get(1) : q.get(3)), q.get(0));
						} else {
							dst.put(new Desc(last0, q.get(0), q.get(1)), q.get(2));
						}
						break;
					default:
						System.err.println("Unsupported type: " + s);
				}
			}
		} catch (Exception e) {
			throw new RuntimeException("Unable to read srg file", e);
		}
	}

	public void saveMap(File file) throws IOException {
		try (OutputStreamWriter ob = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			saveMap(ob);
		}
	}
	public void saveMap(Appendable ob) throws IOException {
		staticSaveMap(classMap, methodMap.entrySet(), fieldMap.entrySet(), checkFieldType, ob);
	}

	public static void staticSaveMap(Map<String, String> classMap,
									 Iterable<Map.Entry<Desc, String>> methodMap, Iterable<Map.Entry<Desc, String>> fieldMap,
									 boolean checkFieldType, Appendable ob) throws IOException {
		MyHashMap<String, CharList> classFos = new MyHashMap<>(classMap.size());

		MapUtil U = MapUtil.getInstance();
		for (Map.Entry<Desc, String> entry : fieldMap) {
			Desc desc = entry.getKey();

			String cn = desc.owner;
			CharList cl = classFos.get(cn);
			if (cl == null) {
				classFos.put(cn, cl = new CharList(100));
			}
			String param = U.mapFieldType(classMap, desc.param);

			String v = entry.getValue();
			// don't write unchanged field
			if (v.equals(desc.name) && param == null) continue;

			if (checkFieldType) {
				cl.append("FL: ").append(desc.name).append(' ').append(desc.param).append(' ')
				  .append(v.equals(desc.name) ? "~" : v).append(' ').append(param == null || param.equals(desc.param) ? "~" : param).append('\n');
			} else {
				cl.append("FL: ").append(desc.name).append(' ').append(v).append('\n');
			}
		}

		for (Map.Entry<Desc, String> entry : methodMap) {
			Desc desc = entry.getKey();

			String cn = desc.owner;
			CharList cl = classFos.get(cn);
			if (cl == null) {
				classFos.put(cn, cl = new CharList(100));
			}
			String param = U.mapMethodParam(classMap, desc.param);

			String v = entry.getValue();
			// don't write unchanged method
			if (v.equals(desc.name) && param.equals(desc.param)) continue;

			cl.append("ML: ").append(desc.name).append(' ').append(desc.param).append(' ')
			  .append(entry.getValue()).append(' ').append(param.equals(desc.param) ? "~" : param).append('\n');
		}

		for (Map.Entry<String, String> entry : classMap.entrySet()) {
			String v = entry.getValue();
			if (v.equals(entry.getKey())) v = "~";
			ob.append("CL: ").append(entry.getKey()).append(' ').append(v).append('\n');
			CharList list = classFos.get(entry.getKey());
			if (list != null) ob.append(list);
		}
	}

	public static void makeInheritMap(Map<String, List<String>> superMap, Map<String, String> filter) {
		MapperList parents = new MapperList();

		SimpleList<String> self = new SimpleList<>();
		SimpleList<String> next = new SimpleList<>();

		// 从一级继承构建所有继承, note: 是所有输入
		for (Iterator<Map.Entry<String, List<String>>> itr = superMap.entrySet().iterator(); itr.hasNext(); ) {
			Map.Entry<String, List<String>> entry = itr.next();
			if (entry.getValue().getClass() == MapperList.class) continue; // done

			String name = entry.getKey();

			self.addAll(entry.getValue());

			int cycle = 0;
			/**
			 * excepted order:
			 *     fatherclass fatheritf grandclass granditf, etc...
			 */
			do {
				if (cycle++ > 30) throw new IllegalStateException("Probably circular reference for " + name + " " + parents);
				parents.addAll(self);
				if ((cycle & 3) == 0) parents.preClean();
				for (int i = 0; i < self.size(); i++) {
					String s = self.get(i);
					Collection<String> tmp;
					if ((tmp = superMap.get(s)) != null) {
						if (tmp.getClass() != MapperList.class) {
							next.addAll(tmp);
							if (cycle > 15 && tmp.contains(s)) throw new IllegalStateException("Circular reference in " + s);
						} else {
							parents.addAll(tmp);
						}
					}
					if (cycle > 15 && next.contains(s)) throw new IllegalStateException("Circular reference in " + s);
				}
				SimpleList<String> tmp1 = self;
				self = next;
				next = tmp1;
				next.clear();
			} while (!self.isEmpty());

			if (filter != null) {
				for (int i = parents.size() - 1; i >= 0; i--) {
					if (!filter.containsKey(parents.get(i))) {
						parents.remove(i); // 删除不存在映射的爹
					}
				}
			}

			if (!parents.isEmpty()) { // 若不是空的，则更新一个
				parents._init_();
				entry.setValue(parents);
				parents = new MapperList();
			} else {
				itr.remove();
			}
		}
	}

	public boolean classNameChanged() {
		for (Map.Entry<String, String> e : classMap.entrySet()) {
			if (!e.getKey().equals(e.getValue())) return true;
		}
		return false;
	}

	public void applyPackageRename() {
		if (packageMap == null) return;

		for (Iterator<Map.Entry<String, String>> itr = classMap.entrySet().iterator(); itr.hasNext(); ) {
			Map.Entry<String, String> entry = itr.next();
			Map.Entry<MutableInt, String> found = packageMap.longestMatches(entry.getValue());
			if (found != null) {
				String k = entry.getKey();
				String v = found.getValue().concat(entry.getValue().substring(found.getKey().getValue()));
				itr.remove();
				classMap.put(k, v);
			}
		}

		packageMap.clear();
	}

	/**
	 * SrgMap data
	 */
	public final Flippable<String, String> getClassMap() {
		return classMap;
	}
	public final FindMap<Desc, String> getFieldMap() {
		return fieldMap;
	}
	public final FindMap<Desc, String> getMethodMap() {
		return methodMap;
	}

	/**
	 * Mapper{A->B} .reverse()   =>>  Mapper{B->A}
	 */
	public void reverseSelf() {
		reverse0(this);
	}

	/**
	 * Mapper{A->B} .reverse()   =>>  Mapper{B->A}
	 */
	public Mapping reverse() {
		Mapping newMap = new Mapping();
		reverse0(newMap);
		return newMap;
	}

	private void reverse0(Mapping dst) {
		if (dst.checkFieldType != checkFieldType) throw new IllegalStateException("checkFieldType are not same");
		MapUtil U = MapUtil.getInstance();

		MyHashMap<Desc, String> fieldMap1 = new MyHashMap<>(fieldMap.size());
		for (Map.Entry<Desc, String> entry : fieldMap.entrySet()) {
			Desc desc = entry.getKey();
			Desc target = new Desc(classMap.getOrDefault(desc.owner, desc.owner), entry.getValue(), desc.param, desc.flags);
			if (checkFieldType) {
				String param = U.mapFieldType(classMap, desc.param);
				if (param != null) target.param = param;
			}
			fieldMap1.put(target, desc.name);
		}
		dst.fieldMap = fieldMap1;

		MyHashMap<Desc, String> methodMap1 = new MyHashMap<>(methodMap.size());
		for (Map.Entry<Desc, String> entry : methodMap.entrySet()) {
			Desc desc = entry.getKey();
			Desc target = new Desc(classMap.getOrDefault(desc.owner, desc.owner), entry.getValue(), U.mapMethodParam(classMap, desc.param), desc.flags);
			methodMap1.put(target, desc.name);
		}
		dst.methodMap = methodMap1;

		dst.classMap = classMap.flip();
	}

	/**
	 * Mapper{B->C} .extend ( Mapper{A->B} )   =>>  Mapper{A->C}
	 */
	public void extend(Mapping from) {
		extend(from, true);
	}
	public void extend(Mapping from, boolean keepNotfound) {
		if (from.checkFieldType != checkFieldType) throw new IllegalStateException("checkFieldType are not same");
		MapUtil U = MapUtil.getInstance();

		Desc md = U.sharedDC;
		md.param = "";
		for (Iterator<Map.Entry<Desc, String>> itr = fieldMap.entrySet().iterator(); itr.hasNext(); ) {
			Map.Entry<Desc, String> entry = itr.next();
			Desc descriptor = entry.getKey();
			md.owner = U.mapClassName(classMap, descriptor.owner);
			md.name = entry.getValue();
			if (checkFieldType) {
				String param = U.mapFieldType(classMap, md.param = descriptor.param);
				if (param != null) md.param = param;
			}

			if (keepNotfound) {
				entry.setValue(from.fieldMap.getOrDefault(md, entry.getValue()));
			} else {
				String v = from.fieldMap.get(md);
				if (v == null) itr.remove();
				else entry.setValue(v);
			}
		}
		for (Iterator<Map.Entry<Desc, String>> itr = methodMap.entrySet().iterator(); itr.hasNext(); ) {
			Map.Entry<Desc, String> entry = itr.next();
			Desc descriptor = entry.getKey();
			md.owner = U.mapClassName(classMap, descriptor.owner);
			md.name = entry.getValue();
			md.param = U.mapMethodParam(classMap, descriptor.param);
			if (keepNotfound) {
				entry.setValue(from.methodMap.getOrDefault(md, entry.getValue()));
			} else {
				String v = from.methodMap.get(md);
				if (v == null) itr.remove();
				else entry.setValue(v);
			}
		}

		for (Iterator<Map.Entry<String, String>> itr = classMap.entrySet().iterator(); itr.hasNext(); ) {
			Map.Entry<String, String> entry = itr.next();
			if (keepNotfound) {
				classMap.put(entry.getKey(), from.classMap.getOrDefault(entry.getValue(), entry.getValue()));
			} else {
				String v = from.classMap.get(entry.getValue());
				if (v == null) itr.remove();
				else classMap.put(entry.getKey(), v);
			}
		}
	}

	public void merge(Mapping other) {
		merge(other, null);
	}
	public void merge(Mapping other, Boolean priority) {
		merge(Collections.singletonList(other), priority);
	}

	public void merge(List<Mapping> others, Boolean priority) {
		MyHashSet<String> ownerMod = new MyHashSet<>();
		ToIntMap<String> replaceSubs = new ToIntMap<>();

		for (int i = 0; i < others.size(); i++) {
			Mapping other = others.get(i);
			if (other.checkFieldType != checkFieldType) throw new IllegalStateException("checkFieldType are not same");
		}

		for (int i = 0; i < others.size(); i++) {
			Mapping other = others.get(i);

			for (Map.Entry<String, String> entry : other.classMap.entrySet()) {
				String v = classMap.putIfAbsent(entry.getKey(), entry.getValue());
				if (v != null) {
					if (!v.equals(entry.getValue())) {
						if (priority == null) throw new UnsupportedOperationException("replace existing name without priority");
						else if (priority) {
							if (entry.getKey().indexOf('$') < 0) ownerMod.add(entry.getKey());
							classMap.put(entry.getKey(), entry.getValue());
						}
					}
				} else {
					replaceSubs.putInt(entry.getKey(), i);
				}
			}

			for (Map.Entry<Desc, String> entry : other.fieldMap.entrySet()) {
				String v = fieldMap.putIfAbsent(entry.getKey(), entry.getValue());
				if (v != null) {
					if (!v.equals(entry.getValue())) {
						if (priority == null) throw new UnsupportedOperationException("replace existing name without priority");
						else if (priority) fieldMap.put(entry.getKey(), entry.getValue());
					}
				}
			}

			for (Map.Entry<Desc, String> entry : other.methodMap.entrySet()) {
				String v = methodMap.putIfAbsent(entry.getKey(), entry.getValue());
				if (v != null) {
					if (!v.equals(entry.getValue())) {
						if (priority == null) throw new UnsupportedOperationException("replace existing name without priority");
						else if (priority) methodMap.put(entry.getKey(), entry.getValue());
					}
				}
			}
		}

		if (!ownerMod.isEmpty()) {
			CharList prefix = IOUtil.getSharedCharBuf();
			for (Map.Entry<String, String> entry : classMap.entrySet()) {
				String name = entry.getKey();

				int pos = name.indexOf('$');
				if (pos < 0) continue;

				prefix.clear();
				prefix.append(name, 0, pos);

				if (ownerMod.contains(prefix) || replaceSubs.containsKey(prefix)) {
					String newClass = classMap.get(prefix);
					int pos1 = entry.getValue().indexOf('$');
					String fixed = newClass.concat(pos1 < 0 ? name.substring(pos) : entry.getValue().substring(pos1));
					System.out.println("前缀冲突: " + prefix + "=>" + newClass + "与其子类" + name.substring(pos) + "=>" + entry.getValue() + "不在同一域中");
					classMap.forcePut(name, fixed);
				}
			}
		}
	}
}
