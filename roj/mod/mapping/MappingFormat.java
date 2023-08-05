package roj.mod.mapping;

import roj.collect.*;
import roj.config.data.CEntry;
import roj.config.data.CMapping;
import roj.config.data.Type;
import roj.mapper.MapUtil;
import roj.mapper.Mapping;
import roj.mapper.util.Desc;
import roj.math.Version;
import roj.text.LineReader;
import roj.text.TextUtil;
import roj.ui.CLIUtil;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Roj234
 * @since 2022/11/1 0001 23:02
 */
public class MappingFormat {
	public final String id, title;
	private final List<VersionRange> versions;
	private final List<MapLoader> loaders;
	private final String[] instructions;

	public boolean hasMCP() {
		for (MapLoader v : loaders) {
			if (v.type.equals("MCP")) {
				return true;
			}
		}
		return false;
	}

	public MappingFormat(CMapping cfg) {
		id = cfg.getString("group");
		title = cfg.getString("title");

		CEntry entry = cfg.get("version");
		if (entry.getType() == Type.STRING) {
			versions = Collections.singletonList(VersionRange.parse(entry.asString()));
		} else {
			List<CEntry> raw = entry.asList().raw();
			versions = Arrays.asList(new VersionRange[raw.size()]);
			for (int i = 0; i < raw.size(); i++) {
				entry = raw.get(i);
				versions.set(i, VersionRange.parse(entry.asString()));
			}
		}

		MyHashSet<String> used = cfg.getOrCreateList("input").asStringSet();
		loaders = new SimpleList<>();
		for (Map.Entry<String, CEntry> input : cfg.getOrCreateMap("mapping").entrySet()) {
			if (used.remove(input.getKey())) {
				loaders.add(MapLoader.parse(input.getKey(), input.getValue().asMap()));
			}
		}
		if (!used.isEmpty()) throw new IllegalStateException("缺少的表: " + used);
		List<String> list = LineReader.slrParserV2(cfg.getString("formula"), true);
		for (int i = list.size() - 1; i >= 0; i--) {
			String s = list.get(i).trim();
			if (s.startsWith("#") || s.isEmpty()) list.remove(i);
			else list.set(i, s);
		}
		instructions = list.toArray(new String[list.size()]);
	}

	public boolean canUseMinecraft(Version v) {
		for (int i = 0; i < versions.size(); i++) {
			if (versions.get(i).suitable(v)) return true;
		}
		return false;
	}

	/**
	// cfg
	// version:  String   / mc version
	// tmp:      List     / tmp list
	// mc_json:  CMapping / mc version json
	// mirror.<n>: String / mirror url for <mirror id>
	// override: String   / mapping overrides
	// notch:    boolean  / generate notch->searge mapping
	 */
	public MapResult map(Map<String,Object> cfg, File tmp) throws IOException {
		cfg.put("id", id);

		Map<String, Mapping> maps = new MyHashMap<>();
		boolean fail = false;
		for (int i = 0; i < loaders.size(); i++) {
			try {
				MapLoader loader = loaders.get(i);
				Mapping m = loader.load(cfg, tmp);
				maps.put(loader.name, m);
			} catch (Exception e) {
				e.printStackTrace();
				fail = true;
			}
		}
		if (fail) return null;

		Map<Object, String> overrides;
		String ovr1 = cfg.getOrDefault("override","").toString();
		if (!ovr1.isEmpty()) {
			overrides = new MyHashMap<>();
			LineReader slr = new LineReader(ovr1, true);
			for (String line : slr) {
				line = line.trim();
				if (line.startsWith("#")) continue;

				int pos = line.indexOf(',');
				if (pos < 0) {
					CLIUtil.warning("override.cfg:"+slr.lineNumber()+": 非法的格式");
					continue;
				}
				overrides.put(line.substring(0,pos).trim(),line.substring(pos+1).trim());
			}
		} else overrides = Collections.emptyMap();

		MapResult mr = new MapResult();
		mr.paramMap = Helpers.cast(cfg.get("param"));

		List<String> arg = Helpers.cast(cfg.get("tmp"));
		for (String key : instructions) {
			arg.clear();
			TextUtil.split(arg, key, ' ');
			switch (arg.get(0)) {
				case "apply_mcp":
					// apply_mcp mcp srg to keep (keeping srg: m2 is not empty)
					boolean b = Boolean.parseBoolean(arg.get(4));
					MCPMapping m1 = (MCPMapping) maps.get(arg.get(1));

					Mapping m = maps.get(arg.get(2));
					Mapping m2 = m;
					if (!arg.get(2).equals(arg.get(3))) {
						m2 = b ? Mapping.fullCopy(m2) : new Mapping();
						maps.put(arg.get(3), m2);
					} else if (!b) {
						m2 = new Mapping();
					}

					m1.apply(arg, m, m2);
					break;
				case "print": case "p":
					System.out.println(maps.get(arg.get(1)).getMethodMap().entrySet().stream().limit(32).map(Object::toString).collect(Collectors.joining("\n ")));
					break;
				case "override": case "o":
					CLIUtil.warning("Legacy Override标记已废除且不再有效果");
					break;
				case "save": case "s":
					// s map kind
					if (cfg.get("notch") == Boolean.FALSE && arg.get(2).equals("deobf")) break;

					m = Mapping.fullCopy(maps.get(arg.get(1)));
					switch (arg.get(2)) {
						case "compile":
							OvrFVRK(overrides, m.getFieldMap());
							OvrFVRK(overrides, m.getMethodMap());

							mr.tsrgCompile = m;
							break;
						case "deobf":
							mr.tsrgDeobf = m;
							break;
					}
					break;
				case "extend": case "e":
					// e a b store keep
					b = Boolean.parseBoolean(arg.get(4));
					if (arg.get(1).equals(arg.get(3))) {
						maps.get(arg.get(1)).extend(maps.get(arg.get(2)), b);
					} else {
						m = Mapping.fullCopy(maps.get(arg.get(1)));
						m.extend(maps.get(arg.get(2)), b);
						maps.put(arg.get(3), m);
					}
					break;
				case "reverse": case "r":
					// r map store
					if (arg.get(1).equals(arg.get(2))) {
						maps.get(arg.get(1)).reverseSelf();
					} else {
						m = maps.get(arg.get(1)).reverse();
						maps.put(arg.get(2), m);
					}
					break;
				case "merge": case "m":
					m = maps.get(arg.get(1));
					if (!arg.get(1).equals(arg.get(arg.size()-1))) {
						m = Mapping.fullCopy(m);
					}
					for (int i = 2; i < arg.size()-1; i++) {
						m.merge(maps.get(arg.get(i)), true);
					}
					maps.put(arg.get(arg.size()-1), m);
					break;
			}
		}
		if (mr.tsrgDeobf != null) OvrFKRV(overrides, mr.tsrgDeobf);
		if (mr.paramMap != null && mr.tsrgCompile != null) {
			MapParam(mr.paramMap, mr.tsrgCompile);
		}

		return mr;
	}

	private static void OvrFVRK(Map<Object, String> ovr, FindMap<Desc, String> map) {
		MyHashSet<Desc> iterated = new MyHashSet<>();
		for (Iterator<Map.Entry<Desc, String>> itr = map.entrySet().iterator(); itr.hasNext(); ) {
			Map.Entry<Desc, String> entry = itr.next();

			Desc k = entry.getKey();
			String v = entry.getValue();

			String name = ovr.get(v);
			if (name != null && !iterated.contains(k)) {
				ovr.put(k.copy(), name);

				itr.remove();

				k.name = name;
				iterated.add(k);
				map.put(k, v);
			}
		}
		for (Desc desc : iterated) ovr.remove(desc.name);
	}
	private static void OvrFKRV(Map<Object, String> ovr, Mapping m) {
		MapUtil U = MapUtil.getInstance();
		Desc md = U.sharedDC;
		Flippable<String, String> cm = m.getClassMap();

		for (Map.Entry<Desc, String> entry : m.getFieldMap().entrySet()) {
			Desc k = entry.getKey();

			md.owner = cm.getOrDefault(k.owner, k.owner);
			md.name = entry.getValue();
			md.param = U.mapMethodParam(cm, k.param);

			String name = ovr.get(md);
			if (name != null) entry.setValue(name);
		}

		for (Map.Entry<Desc, String> entry : m.getMethodMap().entrySet()) {
			Desc k = entry.getKey();

			md.owner = cm.getOrDefault(k.owner, k.owner);
			md.name = entry.getValue();
			md.param = U.mapMethodParam(cm, k.param);

			String name = ovr.get(md);
			if (name != null) entry.setValue(name);
		}
	}
	private static void MapParam(Map<Desc, List<String>> params, Mapping tsrgCompile) {
		Map<String, String> klassRev = tsrgCompile.getClassMap().flip();

		MapUtil u = MapUtil.getInstance();
		Desc md = u.sharedDC;

		for (Map.Entry<Desc, String> entry : tsrgCompile.getMethodMap().entrySet()) {
			Desc k = entry.getKey();
			// useless entry
			if (k.name.equals(entry.getValue())) continue;

			md.owner = klassRev.getOrDefault(k.owner, k.owner);
			md.name = entry.getValue();
			md.param = u.mapMethodParam(klassRev, k.param);

			List<String> param = params.remove(md);
			if (param != null) {
				System.out.println("MappingFormat.java:276: mapped param: " + md + " => " + k.name);
				md.name = k.name;
				params.put(md.copy(), param);
			}
		}
	}

	@Override
	public String toString() {
		return "'"+ title +"' 映射表{version=" + versions + ", mapping=" + loaders + '}';
	}

	public static final class MapResult {
		public Mapping tsrgCompile, tsrgDeobf;
		public Map<Desc, List<String>> paramMap;

		@Override
		public String toString() {
			return "MapResult{" +
				"tsrgCompile=" + tsrgCompile +
				", tsrgDeobf=" + tsrgDeobf +
				", paramMap=" + (paramMap==null?-1:paramMap.size()) + '}';
		}
	}

}
