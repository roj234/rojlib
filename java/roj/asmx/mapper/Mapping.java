package roj.asmx.mapper;

import org.jetbrains.annotations.Nullable;
import roj.asm.ClassUtil;
import roj.asm.MemberDescriptor;
import roj.collect.*;
import roj.config.ParseException;
import roj.config.Tokenizer;
import roj.config.data.CInt;
import roj.io.IOUtil;
import roj.text.*;
import roj.text.logging.Level;
import roj.text.logging.Logger;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Class Mapping (format: XSrg)
 *
 * @author Roj234
 * @since 2020/8/28 19:18
 */
public class Mapping {
	public static record MethodExtra(List<String> arguments, String javadoc) {}

	public static final Logger LOGGER = Logger.getLogger("Mapper");
	static { LOGGER.setLevel(Level.ERROR); }

	public String _name;

	protected Flippable<String, String> classMap;
	protected FindMap<MemberDescriptor, String> fieldMap, methodMap;
	protected TrieTree<String> packageMap;
	protected Map<MemberDescriptor, List<String>> paramMap;
	public boolean checkFieldType;

	public Map<MemberDescriptor, List<String>> getParamMap() {return paramMap;}

	public Mapping copy() {
		Mapping m = new Mapping(checkFieldType);
		m.classMap.putAll(classMap);
		m.fieldMap.putAll(fieldMap);
		m.methodMap.putAll(methodMap);
		m.paramMap.putAll(paramMap);
		if (packageMap != null) m.packageMap = new TrieTree<>(packageMap);
		return m;
	}

	public Mapping() {this(false);}
	public Mapping(boolean checkFieldType) {
		this.checkFieldType = checkFieldType;
		this.classMap = new HashBiMap<>(1000);
		this.fieldMap = new HashMap<>(1000);
		this.methodMap = new HashMap<>(1000);
		this.paramMap = new HashMap<>();
	}

	public Mapping(Mapping o) {
		this.classMap = o.classMap;
		this.fieldMap = o.fieldMap;
		this.methodMap = o.methodMap;
		this.packageMap = o.packageMap;
		this.paramMap = o.paramMap;
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
	public final void loadMap(LineReader slr, boolean reverse) {
		java.util.ArrayList<String> q = new java.util.ArrayList<>();
		String last0 = null, last1 = null;
		MemberDescriptor lastMethod = null;

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
			TextUtil.split(q, s.substring(i + 1).trim(), ' ');

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
						fieldMap.put(new MemberDescriptor(q.get(1).substring(0, id2), q.get(1).substring(id2 + 1)), q.get(0).substring(id + 1));
					} else {
						fieldMap.put(new MemberDescriptor(q.get(0).substring(0, id), q.get(0).substring(id + 1)), q.get(1).substring(id2 + 1));
					}
					break;
				case "MD":
					id = q.get(0).lastIndexOf('/');
					id2 = q.get(2).lastIndexOf('/');

					if (reverse) {
						methodMap.put(new MemberDescriptor(q.get(2).substring(0, id2), q.get(2).substring(id2 + 1), q.get(3)), q.get(0).substring(id + 1));
					} else {
						methodMap.put(new MemberDescriptor(q.get(0).substring(0, id), q.get(0).substring(id + 1), q.get(1)), q.get(2).substring(id2 + 1));
					}
					break;
				case "FL", "F":
					FindMap<MemberDescriptor, String> fm = fieldMap;
					try {
						for (int j = 0; j < q.size(); j++) q.set(j, Tokenizer.unescape(q.get(j)));
					} catch (ParseException e) {
						e.printStackTrace();
					}
					switch (q.size()) {
						case 2:
							if (checkFieldType) throw new IllegalArgumentException("FL(2) is not supported when checkFieldType=true");
							if (reverse) fm.put(new MemberDescriptor(last1, q.get(1)), q.get(0));
							else fm.put(new MemberDescriptor(last0, q.get(0)), q.get(1));
						break;
						case 3: q.add("~");
						case 4:
							if (reverse) fm.put(new MemberDescriptor(last1, q.get(2), !checkFieldType ? "" : q.get(3).equals("~") ? q.get(1) : q.get(3)), q.get(0));
							else fm.put(new MemberDescriptor(last0, q.get(0), !checkFieldType ? "" : q.get(1)), q.get(2));
						break;
					}
					break;
				case "ML", "M":
					FindMap<MemberDescriptor, String> mm = methodMap;
					try {
						for (int j = 0; j < q.size(); j++) q.set(j, Tokenizer.unescape(q.get(j)));
					} catch (ParseException e) {
						e.printStackTrace();
					}
					if (q.size() == 3) q.add("~");
					if (reverse) mm.put(lastMethod = new MemberDescriptor(last1, q.get(2), q.get(3).equals("~") ? q.get(1) : q.get(3)), q.get(0));
					else mm.put(lastMethod = new MemberDescriptor(last0, q.get(0), q.get(1)), q.get(2));
				break;
				case "A":
					ArrayList<String> value = new ArrayList<>(q);
					for (int j = 0; j < value.size(); j++) {
						String arg = value.get(j);
						if (arg.equals("null")) value.set(j, null);
					}
					paramMap.put(lastMethod, value);
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
		HashMap<String, CharList> classFos = new HashMap<>(classMap.size());

		ClassUtil U = ClassUtil.getInstance();
		for (Map.Entry<MemberDescriptor, String> entry : fieldMap.entrySet()) {
			MemberDescriptor d = entry.getKey();

			String cn = d.owner;
			CharList sb = classFos.get(cn);
			if (sb == null) classFos.put(cn, sb = new CharList(100));

			String param = U.mapFieldType(classMap, d.rawDesc);

			String v = entry.getValue();
			// don't write unchanged field
			if (v.equals(d.name) && param == null) continue;

			if (checkFieldType) {
				Tokenizer.escape(sb.append("F: "), d.name);
				Tokenizer.escape(sb.append(' '), d.rawDesc);
				Tokenizer.escape(sb.append(' '), v);
				if (param != null) Tokenizer.escape(sb.append(' '), param);
				sb.append('\n');
			} else {
				Tokenizer.escape(sb.append("F: "), d.name);
				Tokenizer.escape(sb.append(' '), v).append('\n');
			}
		}

		for (Map.Entry<MemberDescriptor, String> entry : methodMap.entrySet()) {
			MemberDescriptor d = entry.getKey();

			String cn = d.owner;
			CharList sb = classFos.get(cn);
			if (sb == null) classFos.put(cn, sb = new CharList(100));

			String param = U.mapMethodParam(classMap, d.rawDesc);

			String v = entry.getValue();
			// don't write unchanged method
			if (v.equals(d.name) && param.equals(d.rawDesc)) continue;

			Tokenizer.escape(sb.append("M: "), d.name);
			Tokenizer.escape(sb.append(' '), d.rawDesc);
			Tokenizer.escape(sb.append(' '), entry.getValue());
			if (!param.equals(d.rawDesc)) Tokenizer.escape(sb.append(' '), param);
			sb.append('\n');

			List<String> args = paramMap.get(d);
			if (args != null && !args.isEmpty()) {
				sb.append("A: ");
				for (int i = 0; i < args.size();) {
					sb.append(args.get(i)).append(++i == args.size() ? '\n' : ' ');
				}
			}
		}

		for (Map.Entry<String, String> entry : classMap.entrySet()) {
			String v = entry.getValue();
			if (v.equals(entry.getKey())) v = "~";
			Tokenizer.escape(ob.append("CL: "), entry.getKey());
			Tokenizer.escape(ob.append(' '), v).append('\n');
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

			/*
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
			Map.Entry<CInt, String> found = packageMap.longestMatches(entry.getValue());
			if (found != null) {
				String k = entry.getKey();
				String v = found.getValue().concat(entry.getValue().substring(found.getKey().value));
				itr.remove();
				classMap.put(k, v);
			}
		}

		packageMap.clear();
	}

	public final Flippable<String, String> getClassMap() {
		return classMap;
	}
	public final FindMap<MemberDescriptor, String> getFieldMap() {
		return fieldMap;
	}
	public final FindMap<MemberDescriptor, String> getMethodMap() {
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

		HashMap<MemberDescriptor, String> fieldMap1 = new HashMap<>(fieldMap.size());
		for (Map.Entry<MemberDescriptor, String> entry : fieldMap.entrySet()) {
			MemberDescriptor desc = entry.getKey();
			MemberDescriptor target = new MemberDescriptor(classMap.getOrDefault(desc.owner, desc.owner), entry.getValue(), desc.rawDesc, desc.modifier);
			if (checkFieldType) {
				String param = U.mapFieldType(classMap, desc.rawDesc);
				if (param != null) target.rawDesc = param;
			}
			fieldMap1.put(target, desc.name);
		}
		dst.fieldMap = fieldMap1;

		HashMap<MemberDescriptor, String> methodMap1 = new HashMap<>(methodMap.size());
		HashMap<MemberDescriptor, List<String>> paramMap1 = new HashMap<>(paramMap.size());
		for (var entry : methodMap.entrySet()) {
			var desc = entry.getKey();
			var target = new MemberDescriptor(classMap.getOrDefault(desc.owner, desc.owner), entry.getValue(), U.mapMethodParam(classMap, desc.rawDesc), desc.modifier);
			methodMap1.put(target, desc.name);
			var data = paramMap1.get(desc);
			if (data != null) paramMap1.put(target, data);
		}
		dst.methodMap = methodMap1;

		dst.classMap = classMap.flip();
	}

	public void deleteClassMap() {
		var newMap = new HashBiMap<String, String>();
		for (Map.Entry<String, String> entry : classMap.entrySet()) {
			newMap.put(entry.getKey(), entry.getKey());
		}
		classMap = newMap;
	}

	/**
	 * Mapper{A->B} .extend ( Mapper{B->C} )   =>>  Mapper{A->C}
	 */
	public void extend(Mapping from) {
		extend(from, true);
	}
	public void extend(Mapping from, boolean keepNotfound) {
		if (from.checkFieldType != checkFieldType) throw new IllegalStateException("checkFieldType are not same");
		ClassUtil U = ClassUtil.getInstance();

		MemberDescriptor d = U.sharedDesc;
		d.rawDesc = "";
		for (Iterator<Map.Entry<MemberDescriptor, String>> itr = fieldMap.entrySet().iterator(); itr.hasNext(); ) {
			Map.Entry<MemberDescriptor, String> entry = itr.next();
			MemberDescriptor fd = entry.getKey();
			String nn = U.mapClassName(classMap, fd.owner);
			d.owner = nn != null ? nn : fd.owner;
			d.name = entry.getValue();
			if (checkFieldType) {
				String param = U.mapFieldType(classMap, d.rawDesc = fd.rawDesc);
				if (param != null) d.rawDesc = param;
			}

			if (keepNotfound) {
				entry.setValue(from.fieldMap.getOrDefault(d, entry.getValue()));
			} else {
				String v = from.fieldMap.get(d);
				if (v == null) itr.remove();
				else entry.setValue(v);
			}
		}
		for (var itr = methodMap.entrySet().iterator(); itr.hasNext(); ) {
			var entry = itr.next();
			MemberDescriptor md = entry.getKey();
			String nn = U.mapClassName(classMap, md.owner);
			d.owner = nn != null ? nn : md.owner;
			d.name = entry.getValue();
			d.rawDesc = U.mapMethodParam(classMap, md.rawDesc);
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
		HashSet<String> ownerMod = new HashSet<>();
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

		for (Mapping other : others) {
			if (priority == Boolean.TRUE) paramMap.putAll(other.paramMap);
			else for (var entry : other.paramMap.entrySet()) {
				var prev = paramMap.putIfAbsent(entry.getKey(), entry.getValue());
				if (prev != null && priority == null) throw new UnsupportedOperationException("replace existing name without priority");
			}
		}
	}

	private void mergeFM(FindMap<MemberDescriptor, String> other, FindMap<MemberDescriptor, String> fieldMap, Boolean priority) {
		for (Map.Entry<MemberDescriptor, String> entry : other.entrySet()) {
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