package roj.plugins.ci.minecraft;

import org.jetbrains.annotations.NotNull;
import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.archive.zip.ZipFileWriter;
import roj.asm.Parser;
import roj.asm.type.Desc;
import roj.asm.util.Context;
import roj.asm.util.TransformUtil;
import roj.asmx.event.Subscribe;
import roj.asmx.mapper.Mapper;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.concurrent.OperationDone;
import roj.config.data.CEntry;
import roj.io.IOUtil;
import roj.plugins.ci.Compiler;
import roj.plugins.ci.FMD;
import roj.plugins.ci.Project;
import roj.plugins.ci.event.ProjectUpdateEvent;
import roj.plugins.ci.plugin.ProcessEnvironment;
import roj.plugins.ci.plugin.Processor;
import roj.text.TextReader;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

import static roj.asmx.mapper.Mapper.DONT_LOAD_PREFIX;
import static roj.plugins.ci.FMD.*;

/**
 * Text AT Processor
 *
 * @author Roj234
 * @since 2021/5/30 19:59
 */
public class AT implements Processor {
	public AT() {EVENT_BUS.register(this);}

	@Subscribe
	public void onIMLUpdate(ProjectUpdateEvent event) {
		event.add("at-"+event.getProject().getName()+".jar");
	}

	@Override public String name() {return "AccessTransformer";}
	@Override public void init(CEntry config) {archives.clear();}

	@Override
	public int beforeCompile(Compiler compiler, SimpleList<String> options, List<File> files, ProcessEnvironment pc) {
		Project p = pc.project;
		var atFile = new File(p.getResPath(), p.variables.getOrDefault("fmd:at:path", "META-INF/accesstransformer.cfg"));
		if (atFile.isFile() && "true".equals(p.variables.get("fmd:at"))) {
			try {
				var atList = buildATMapFromATCfg(atFile, p.workspace.getInvMapper());
				makeAT(options, atList, p.getName(), p);
			} catch (IOException e) {
				LOGGER.error("加载AT失败", e);
			}
		}

		return 2;
	}

	@NotNull
	static Map<String, List<String>> buildATMapFromATCfg(Object atFile, Mapper map) {
		Map<String, List<String>> atList = new MyHashMap<>();
		try (var tr = makeTextReader(atFile)) {
			while (true) {
				String line = tr.readLine();
				if (line == null) break;
				if (line.startsWith("#")) continue;

				var data = TextUtil.split(line, ' ');
				if (data.size() < 2) continue;

				String className = data.get(1).replace('.', '/');
				List<String> strings = atList.computeIfAbsent(className+".class", Helpers.fnArrayList());
				if (strings.isEmpty()) strings.add("<$extend>");
				if (data.size() > 2 && !data.get(2).contains("#")) {
					String e = data.get(2);

					int i = e.indexOf('(');
					if (i > 0) {
						Map.Entry<Desc, String> mcpName = map.getMethodMap().find(new Desc(className, e.substring(0, i), e.substring(i)));
						if (mcpName != null) e = mcpName.getValue()+mcpName.getKey().rawDesc();
						else if (!e.startsWith("<")) LOGGER.debug("未找到该字段的MCP名称: {}.{}", className, e);
					} else {
						Map.Entry<Desc, String> mcpName = map.getFieldMap().find(new Desc(className, e));
						if (mcpName != null) e = mcpName.getValue();
						else LOGGER.debug("未找到该字段的MCP名称: {}.{}", className, e);
					}
					strings.add(e);
				}

				int pos = className.lastIndexOf('$');
				var initialName = className;
				while (pos >= 0) {
					className = className.substring(0, pos);
					atList.computeIfAbsent(className+".class", Helpers.fnArrayList()).add(initialName);
					pos = className.lastIndexOf('$');
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return atList;
	}
	private static TextReader makeTextReader(Object file) throws IOException {
		return file instanceof File f ? TextReader.auto(f) : new TextReader((Closeable) file, null);
	}

	private void makeAT(SimpleList<String> options, Map<String, List<String>> atList, String name1, Project p) {
		ok:
		if (!atList.isEmpty()) {
			var atJar = new File(FMD.BASE, "data/at-"+name1+".jar");
			if (options != null) {
				int i = options.indexOf("-cp")+1;
				options.set(i, atJar.getAbsolutePath()+File.pathSeparatorChar+options.get(i));
			}

			try (var za = new ZipArchive(atJar)) {
				InputStream in = za.getStream(".desc");
				if (in != null && IOUtil.getSharedByteBuf().readStreamFully(in).readInt() == atList.hashCode()) {
					break ok;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

			try (var zfw = new ZipFileWriter(atJar)) {
				zfw.writeNamed(".desc", (ByteList) new ByteList().putInt(atList.hashCode()));

				Predicate<File> predicate = file -> {
					String name = file.getName().toLowerCase(Locale.ROOT);
					if (!name.startsWith(DONT_LOAD_PREFIX) && (name.endsWith(".zip") || name.endsWith(".jar"))) {
						tryAt(file, atList, zfw);
						if (atList.isEmpty()) throw OperationDone.INSTANCE;
					}
					return false;
				};

				for (var file : p.workspace.depend) {
					tryAt(file, atList, zfw);
				}
				for (var file : p.workspace.mappedDepend) {
					tryAt(file, atList, zfw);
				}
				IOUtil.findAllFiles(new File(BASE, "libs"), predicate);
				IOUtil.findAllFiles(new File(p.getRoot(), "libs"), predicate);
			} catch (OperationDone ignored) {

			} catch (IOException e) {
				e.printStackTrace();
			}

			if (!atList.isEmpty())
				LOGGER.warn("Some ATs are not match: {}", atList);
		}
	}

	private Map<File, Arch> archives = new MyHashMap<>();
	private void tryAt(File file, Map<String, List<String>> list, ZipFileWriter zfw) {
		Arch helper = archives.computeIfAbsent(file, Arch::new);
		try {
			if (!helper.open()) archives.remove(file);
			for (var itr = list.entrySet().iterator(); itr.hasNext(); ) {
				var entry = itr.next();

				InputStream stream = helper.getStream(entry.getKey());
				if (stream != null) {
					LOGGER.debug("Found AT {} in {}", entry.getKey(), file);
					var data = Parser.parseConstants(IOUtil.getSharedByteBuf().readStreamFully(stream));

					TransformUtil.makeAccessible(data, entry.getValue());
					//if (!forIDE) TransformUtil.apiOnly(data);

					zfw.writeNamed(entry.getKey(), Parser.toByteArrayShared(data));

					itr.remove();
				}
			}
			helper.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	static final class Arch {
		private final File file;
		private ZipArchive zf;
		private long lastModify;

		private Arch(File file) {
			this.file = file;
		}

		private InputStream getStream(String name) throws IOException {
			if (zf == null && !open()) return null;
			ZEntry entry = zf.getEntry(name);
			if (entry != null) {
				if (open()) return zf.getStream(entry);
			}
			return null;
		}

		private boolean open() throws IOException {
			if (!file.isFile()) {
				close();
				return false;
			} else {
				long lastMod = file.lastModified();

				if (zf != null) {
					zf.reopen();

					if (lastMod != lastModify) {
						zf.reload();
						lastModify = lastMod;
					}
				} else {
					zf = new ZipArchive(file, ZipArchive.FLAG_BACKWARD_READ);
					lastModify = lastMod;
				}
			}

			return true;
		}

		private void close() throws IOException {zf.close();}
	}


	@Override public List<Context> process(List<Context> classes, ProcessEnvironment ctx) {return classes;}
}