package roj.asmx.mapper;

import org.jetbrains.annotations.Nullable;
import roj.asm.type.Desc;
import roj.asm.util.ClassUtil;
import roj.asmx.mapper.util.MapperList;
import roj.collect.*;
import roj.config.ParseException;
import roj.config.word.ITokenizer;
import roj.io.IOUtil;
import roj.math.MutableInt;
import roj.text.*;
import roj.text.logging.Level;
import roj.text.logging.Logger;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Class Mapping (format: XSrg)
 *
 * @author Roj234
 * @since 2020/8/28 19:18
 */
public class Mapping {
	public static final Logger LOGGER = Logger.getLogger("Mapper");
	static { LOGGER.setLevel(Level.ERROR); }

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
		try (TextReader in = TextReader.auto(path)) {
			loadMap(in, reverse);
		} catch (IOException e) {
			throw new RuntimeException("Unable to read mapping file", e);
		}
	}
	public final void loadMap(InputStream in, boolean reverse) {
		try (TextReader in2 = TextReader.auto(in)) {
			loadMap(in2, reverse);
		} catch (IOException e) {
			throw new RuntimeException("Unable to read mapping file", e);
		}
	}
	@SuppressWarnings("fallthrough")
	public final void loadMap(LinedReader slr, boolean reverse) {
		ArrayList<String> q = new ArrayList<>();
		String last0 = null, last1 = null;

		while (true) {
			String s;
			try {
				s = slr.readLine();
			} catch (Exception e) {
				Helpers.athrow(e);
				return;
			}
			if (s == null) break;

			int i = s.indexOf(':');
			String key = s.substring(0, i);

			q.clear();
			TextUtil.split(q, s.substring(i + 1), ' ');

			int id, id2;
			switch (key) {
				case "PK": // package
					if (!q.get(0).equals(q.get(1))) {
						if (packageMap == null) packageMap = new TrieTree<>();

						if (reverse) packageMap.put(q.get(1), q.get(0));
						else packageMap.put(q.get(0), q.get(1));
					}
					break;
				case "CL": // class
					if (q.get(1).equals("~")) q.set(1, q.get(0));

					if (reverse) classMap.put(q.get(1), q.get(0));
					else classMap.put(q.get(0), q.get(1));
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
				case "FL": case "F":
					FindMap<Desc, String> fm = fieldMap;
					try {
						for (int j = 0; j < q.size(); j++) q.set(j, ITokenizer.removeSlashes(q.get(j)));
					} catch (ParseException e) {
						e.printStackTrace();
					}
					switch (q.size()) {
						case 2:
							if (checkFieldType) throw new IllegalArgumentException("FL(2) is not supported when checkFieldType=true");
							if (reverse) fm.put(new Desc(last1, q.get(1)), q.get(0));
							else fm.put(new Desc(last0, q.get(0)), q.get(1));
						break;
						case 3: q.add("~");
						case 4:
							if (reverse) fm.put(new Desc(last1, q.get(2), !checkFieldType ? "" : q.get(3).equals("~") ? q.get(1) : q.get(3)), q.get(0));
							else fm.put(new Desc(last0, q.get(0), !checkFieldType ? "" : q.get(1)), q.get(2));
						break;
					}
					break;
				case "ML": case "M":
					FindMap<Desc, String> mm = methodMap;
					try {
						for (int j = 0; j < q.size(); j++) q.set(j, ITokenizer.removeSlashes(q.get(j)));
					} catch (ParseException e) {
						e.printStackTrace();
					}
					if (q.size() == 3) q.add("~");
					if (reverse) mm.put(new Desc(last1, q.get(2), q.get(3).equals("~") ? q.get(1) : q.get(3)), q.get(0));
					else mm.put(new Desc(last0, q.get(0), q.get(1)), q.get(2));
				break;
				default:
					System.err.println("Unsupported type: " + s);
			}
		}
	}

	public void saveMap(File file) throws IOException {
		try (TextWriter ob = TextWriter.to(file)) { saveMap(ob); }
	}
	public void saveMap(Appendable ob) throws IOException {
		staticSaveMap(classMap, methodMap.entrySet(), fieldMap.entrySet(), checkFieldType, ob);
	}

	public static void staticSaveMap(Map<String, String> classMap,
									 Iterable<Map.Entry<Desc, String>> methodMap, Iterable<Map.Entry<Desc, String>> fieldMap,
									 boolean checkFieldType, Appendable ob) throws IOException {
		MyHashMap<String, CharList> classFos = new MyHashMap<>(classMap.size());

		ClassUtil U = ClassUtil.getInstance();
		for (Map.Entry<Desc, String> entry : fieldMap) {
			Desc d = entry.getKey();

			String cn = d.owner;
			CharList sb = classFos.get(cn);
			if (sb == null) classFos.put(cn, sb = new CharList(100));

			String param = U.mapFieldType(classMap, d.param);

			String v = entry.getValue();
			// don't write unchanged field
			if (v.equals(d.name) && param == null) continue;

			if (checkFieldType) {
				ITokenizer.addSlashes(sb.append("F: "), d.name);
				ITokenizer.addSlashes(sb.append(' '), d.param);
				ITokenizer.addSlashes(sb.append(' '), v);
				if (param != null) ITokenizer.addSlashes(sb.append(' '), param);
				sb.append('\n');
			} else {
				ITokenizer.addSlashes(sb.append("F: "), d.name);
				ITokenizer.addSlashes(sb.append(' '), v).append('\n');
			}
		}

		for (Map.Entry<Desc, String> entry : methodMap) {
			Desc d = entry.getKey();

			String cn = d.owner;
			CharList sb = classFos.get(cn);
			if (sb == null) classFos.put(cn, sb = new CharList(100));

			String param = U.mapMethodParam(classMap, d.param);

			String v = entry.getValue();
			// don't write unchanged method
			if (v.equals(d.name) && param.equals(d.param)) continue;

			ITokenizer.addSlashes(sb.append("M: "), d.name);
			ITokenizer.addSlashes(sb.append(' '), d.param);
			ITokenizer.addSlashes(sb.append(' '), entry.getValue());
			if (!param.equals(d.param)) ITokenizer.addSlashes(sb.append(' '), param);
			sb.append('\n');
		}

		for (Map.Entry<String, String> entry : classMap.entrySet()) {
			String v = entry.getValue();
			if (v.equals(entry.getKey())) v = "~";
			ITokenizer.addSlashes(ob.append("CL: "), entry.getKey());
			ITokenizer.addSlashes(ob.append(' '), v).append('\n');
			CharList list = classFos.get(entry.getKey());
			if (list != null) ob.append(list);
		}
	}

	public static void makeInheritMap(Map<String, List<String>> superMap, @Nullable Map<String, String> filter) {
		MapperList self = new MapperList();

		for (Iterator<Map.Entry<String, List<String>>> itr = superMap.entrySet().iterator(); itr.hasNext(); ) {
			Map.Entry<String, List<String>> entry = itr.next();
			if (entry.getValue().getClass() == MapperList.class) continue; // done

			self.batchAddFiltered(entry.getValue());
			self.pack0();

			/**
			 * excepted order:
			 *     fatherclass fatheritf grandclass granditf, etc...
			 */
			int i = 0;
			do {
				int nextPos = self.size();
				while (i < nextPos) {
					List<String> tmp = superMap.get(self.get(i++));
					if (tmp != null) self.batchAddFiltered(tmp);
				}
			} while (i < self.size());

			if (filter != null) {
				self.batchRemoveFiltered(filter);
				if (self.isEmpty()) {
					itr.remove();
					continue;
				}
			}

			self.trimToSize();
			entry.setValue(self);

			self = new MapperList();
		}
	}

	public boolean classNameChanged() {
		for (Map.Entry<String, String> e : classMap.entrySet()) {
			if (!e.getKey().equals(e.getValue())) return true;
		}
		return false;
	}

	public void applyPackageRename() {
		if (packageMap == null || packageMap.isEmpty()) return;

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
		ClassUtil U = ClassUtil.getInstance();

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
		ClassUtil U = ClassUtil.getInstance();

		Desc d = U.sharedDC;
		d.param = "";
		for (Iterator<Map.Entry<Desc, String>> itr = fieldMap.entrySet().iterator(); itr.hasNext(); ) {
			Map.Entry<Desc, String> entry = itr.next();
			Desc fd = entry.getKey();
			String nn = U.mapClassName(classMap, fd.owner);
			d.owner = nn != null ? nn : fd.owner;
			d.name = entry.getValue();
			if (checkFieldType) {
				String param = U.mapFieldType(classMap, d.param = fd.param);
				if (param != null) d.param = param;
			}

			if (keepNotfound) {
				entry.setValue(from.fieldMap.getOrDefault(d, entry.getValue()));
			} else {
				String v = from.fieldMap.get(d);
				if (v == null) itr.remove();
				else entry.setValue(v);
			}
		}
		for (Iterator<Map.Entry<Desc, String>> itr = methodMap.entrySet().iterator(); itr.hasNext(); ) {
			Map.Entry<Desc, String> entry = itr.next();
			Desc md = entry.getKey();
			String nn = U.mapClassName(classMap, md.owner);
			d.owner = nn != null ? nn : md.owner;
			d.name = entry.getValue();
			d.param = U.mapMethodParam(classMap, md.param);
			if (keepNotfound) {
				entry.setValue(from.methodMap.getOrDefault(d, entry.getValue()));
			} else {
				String v = from.methodMap.get(d);
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

	public void merge(Mapping other) { merge(other, null); }
	public void merge(Mapping other, Boolean priority) { merge(Collections.singletonList(other), priority); }

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

			mergeFM(other.fieldMap, fieldMap, priority);
			mergeFM(other.methodMap, methodMap, priority);
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

	private void mergeFM(FindMap<Desc, String> other, FindMap<Desc, String> fieldMap, Boolean priority) {
		for (Map.Entry<Desc, String> entry : other.entrySet()) {
			String v = fieldMap.putIfAbsent(entry.getKey(), entry.getValue());
			if (v != null) {
				if (!v.equals(entry.getValue())) {
					if (priority == null) throw new UnsupportedOperationException("replace existing name without priority");
					else if (priority) fieldMap.put(entry.getKey(), entry.getValue());
				}
			}
		}
	}
}