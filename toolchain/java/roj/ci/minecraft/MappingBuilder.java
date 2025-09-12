package roj.ci.minecraft;

import roj.asmx.mapper.Mapper;
import roj.asmx.mapper.Mapping;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.config.ConfigMaster;
import roj.config.Parser;
import roj.config.node.IntValue;
import roj.io.IOUtil;
import roj.text.ParseException;
import roj.text.TextReader;
import roj.text.TextUtil;
import roj.util.function.ExceptionalSupplier;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * @author Roj234
 * @since 2025/07/25 19:41
 */
public class MappingBuilder {
	File baseDir;
	Map<String, Mapping> mappings = new HashMap<>();
	Map<String, ExceptionalSupplier<File, IOException>> context;

	public MappingBuilder(File baseDir, Map<String, ExceptionalSupplier<File, IOException>> context) {
		this.baseDir = baseDir;
		this.context = context;
	}

	public Mapping build(File conf) throws IOException, ParseException {
		var config = ConfigMaster.fromExtension(conf).parser().parse(conf, Parser.ORDERED_MAP).asMap();
		for (var entry : config.entrySet()) {
			recursiveBuildMappingEntry(entry.getKey(), TextUtil.split(entry.getValue().asString(), '-'));
		}
		return Objects.requireNonNull(mappings.get("output"), "no output defined");
	}

	private void recursiveBuildMappingEntry(String name, List<String> instructions) throws IOException {
		Mapping m;
		if (instructions.get(0).charAt(0) == 'L') {
			String path = TextUtil.join(instructions.subList(2, instructions.size()), "-");
			var supplier = context.get(path);
			File input = supplier != null ? supplier.get() : IOUtil.relativePath(baseDir, path);
			String type = instructions.get(1).toLowerCase(Locale.ROOT);
			switch (type) {
				case "srg", "xsrg":
					m = new Mapping();
					m.loadMap(input, false);
					break;
				case "tsrg": {
					TSrgMapping m1 = new TSrgMapping();
					m1.readMcpConfig(input, m1.getParamMap(), new ArrayList<>());
					m = m1;
				}
				break;
				case "cache": {
					Mapper m1 = new Mapper();
					try (var src = new FileInputStream(input)) {
						m1.loadCache(src, true);
					}
					m = m1;
				}
				break;
				case "intermediary": {
					YarnMapping m1 = new YarnMapping();
					m1.readIntermediaryMap(input.getName(), TextReader.auto(input), new ArrayList<>());
					m = m1;
				}
				break;
				case "yarn": {
					YarnMapping m1 = new YarnMapping();
					m1.readYarnMap(input, new ArrayList<>(), null, m1.getParamMap());
					m = m1;
				}
				break;
				case "mojang": {
					OjngMapping m1 = new OjngMapping();
					m1.readMojangMap(input.getName(), TextReader.auto(input), new ArrayList<>());
					m = m1;
				}
				break;
				default: throw new UnsupportedOperationException("unknown mapping type "+type);

			}
			m._name = name;
		}else {
			m = recursiveBuildMapping(instructions, new IntValue(0), mappings);
		}
		mappings.put(name, m);
	}

	private Mapping recursiveBuildMapping(List<String> instructions, IntValue index, Map<String, Mapping> mappings) {
		String s = instructions.get(index.value++);
		return switch (s.charAt(0)) {
				case 'E' -> {
					Mapping a = recursiveBuildMapping(instructions, index, mappings);
					Mapping b = recursiveBuildMapping(instructions, index, mappings);
					Mapping out = a.copy();
					out.extend(b, true);
					out._name = "E-"+b._name+"-"+a._name;
					yield out;
				}
				case 'M' -> {
					int count = s.charAt(1) - '0';
					Mapping out = new Mapping();
					for (int i = 0; i < count; i++) {
						var child = recursiveBuildMapping(instructions, index, mappings);
						out.merge(child, true);
						out._name += "-"+child._name;
					}
					out._name = "M-"+count+out._name;
					yield out;
				}
				case 'F' -> {
					Mapping a = recursiveBuildMapping(instructions, index, mappings);
					Mapping out = a.reverse();
					out._name = "F-"+a._name;
					yield out;
				}
				case 'D' -> {
					boolean deleteRight = s.charAt(1) == 'r';
					Mapping out = recursiveBuildMapping(instructions, index, mappings);
					if (deleteRight) {
						out = out.copy();
						out.deleteClassMap();
					} else {
						out = out.reverse();
						out.deleteClassMap();
						out.reverseSelf();
					}
					yield out;
				}
				case 'A' -> {
					Mapping dst = recursiveBuildMapping(instructions, index, mappings).copy();
					Mapping src = recursiveBuildMapping(instructions, index, mappings);
					dst.getParamMap().putAll(src.getParamMap());
					yield dst;
				}
			default -> Objects.requireNonNull(mappings.get(s), s);
		};
	}
}
