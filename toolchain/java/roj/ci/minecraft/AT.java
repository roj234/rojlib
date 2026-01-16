package roj.ci.minecraft;

import org.jetbrains.annotations.NotNull;
import roj.archive.zip.ZipEditor;
import roj.archive.zip.ZipEntry;
import roj.archive.zip.ZipPacker;
import roj.asm.AsmCache;
import roj.asm.ClassNode;
import roj.asm.MemberDescriptor;
import roj.asmx.TransformUtil;
import roj.asmx.mapper.Mapping;
import roj.ci.BuildContext;
import roj.ci.Dependency;
import roj.ci.Project;
import roj.ci.event.ProjectUpdateEvent;
import roj.ci.plugin.Plugin;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.config.node.ConfigValue;
import roj.event.Subscribe;
import roj.io.IOUtil;
import roj.text.TextReader;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.Helpers;
import roj.util.OperationDone;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static roj.asmx.mapper.Mapper.DONT_LOAD_PREFIX;
import static roj.ci.MCMake.*;

/**
 * Text AT Processor
 *
 * @author Roj234
 * @since 2021/5/30 19:59
 */
public class AT implements Plugin {
	public AT() {EVENT_BUS.register(this);}

	@Subscribe
	public void onIMLUpdate(ProjectUpdateEvent event) {
		if (!event.getProject().workspace.hasPlugin(this)) return;
		event.add(event.getProject().getSafeName()+"/at.jar");
	}

	@Override public String name() {return "AccessTransformer";}
	@Override public void init(ConfigValue config) {archives.clear();}

	@Override
	public boolean preProcess(ArrayList<String> options, List<File> sources, BuildContext ctx) {
		Project p = ctx.project;
		var atFile = new File(p.resPath, p.getVariables().getOrDefault("fmd:at", "META-INF/accesstransformer.cfg"));
		if (atFile.isFile()) {
			try {
				var atList = buildATMapFromATCfg(atFile, p.workspace.getInverseMapping());
				makeAT(options, atList, p);
			} catch (IOException e) {
				log.error("加载AT失败", e);
			}
		}

		return false;
	}

	@NotNull
	static Map<String, List<String>> buildATMapFromATCfg(Object atFile, Mapping map) {
		Map<String, List<String>> atList = new HashMap<>();
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
						var desc = new MemberDescriptor(className, e.substring(0, i), e.substring(i));
						Map.Entry<MemberDescriptor, String> mcpName = map.getMethodMap().find(desc);
						if (mcpName != null) e = mcpName.getValue()+mcpName.getKey().rawDesc();
						else if (!e.startsWith("<")) log.info("未找到成员 {} 的MCP名称", desc);
					} else {
						var desc = new MemberDescriptor(className, e);
						Map.Entry<MemberDescriptor, String> mcpName = map.getFieldMap().find(desc);
						if (mcpName != null) e = mcpName.getValue();
						else log.info("未找到成员 {} 的MCP名称", desc);
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

	private void makeAT(ArrayList<String> options, Map<String, List<String>> atList, Project p) {
		ok:
		if (!atList.isEmpty()) {
			var atJar = new File(p.cachePath, "at.jar");
			if (options != null) {
				int i = options.indexOf("-cp")+1;
				options.set(i, atJar.getAbsolutePath()+File.pathSeparatorChar+options.get(i));
			}

			try (var za = new ZipEditor(atJar)) {
				InputStream in = za.getInputStream(".desc");
				if (in != null && IOUtil.getSharedByteBuf().readStreamFully(in).readInt() == atList.hashCode()) {
					break ok;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

			try (var zfw = new ZipPacker(atJar)) {
				zfw.writeNamed(".desc", new ByteList().putInt(atList.hashCode()));

				BiConsumer<String, BasicFileAttributes> callback = (pathname, attr) -> {
					String name = IOUtil.getName(pathname).toLowerCase(Locale.ROOT);
					if (!name.startsWith(DONT_LOAD_PREFIX) && (name.endsWith(".zip") || name.endsWith(".jar"))) {
						tryAt(new File(pathname), atList, zfw);
						if (atList.isEmpty()) throw OperationDone.INSTANCE;
					}
				};

				for (var file : p.workspace.getDepend()) {
					tryAt(file, atList, zfw);
				}
				for (var file : p.workspace.getMappedDepend()) {
					tryAt(file, atList, zfw);
				}
				IOUtil.listPaths(new File(BASE, "lib"), callback);
				Consumer<File> fileConsumer = file -> callback.accept(file.getAbsolutePath(), null);
				for (Dependency dep : p.getCompileDependencies()) {
					dep.forEachJar(fileConsumer);
				}
			} catch (OperationDone ignored) {

			} catch (IOException e) {
				e.printStackTrace();
			}

			if (!atList.isEmpty())
				log.warn("Some ATs are not match: {}", atList);
		}
	}

	private Map<File, Arch> archives = new HashMap<>();
	private void tryAt(File file, Map<String, List<String>> list, ZipPacker zfw) {
		Arch helper = archives.computeIfAbsent(file, Arch::new);
		try {
			if (!helper.open()) archives.remove(file);
			for (var itr = list.entrySet().iterator(); itr.hasNext(); ) {
				var entry = itr.next();

				InputStream stream = helper.getStream(entry.getKey());
				if (stream != null) {
					log.debug("Found AT {} in {}", entry.getKey(), file);
					var data = ClassNode.parseSkeleton(IOUtil.getSharedByteBuf().readStreamFully(stream));

					TransformUtil.makeAccessible(data, entry.getValue());
					//if (!forIDE) TransformUtil.apiOnly(data);

					zfw.writeNamed(entry.getKey(), AsmCache.toByteArrayShared(data));

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
		private ZipEditor zf;
		private long lastModify;

		private Arch(File file) {
			this.file = file;
		}

		private InputStream getStream(String name) throws IOException {
			if (zf == null && !open()) return null;
			ZipEntry entry = zf.getEntry(name);
			if (entry != null) {
				if (open()) return zf.getInputStream(entry);
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
					zf.ensureOpen();

					if (lastMod != lastModify) {
						zf.reload();
						lastModify = lastMod;
					}
				} else {
					zf = new ZipEditor(file, ZipEditor.FLAG_ReadCENOnly);
					lastModify = lastMod;
				}
			}

			return true;
		}

		private void close() throws IOException {zf.close();}
	}
}