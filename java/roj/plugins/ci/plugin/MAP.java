package roj.plugins.ci.plugin;

import roj.archive.zip.ZipArchive;
import roj.archive.zip.ZipFileWriter;
import roj.asm.type.Desc;
import roj.asm.util.Context;
import roj.asmx.event.Subscribe;
import roj.asmx.mapper.Mapper;
import roj.collect.SimpleList;
import roj.config.data.CEntry;
import roj.crypt.ILCrypto;
import roj.io.IOUtil;
import roj.plugins.ci.Compiler;
import roj.plugins.ci.FMD;
import roj.plugins.ci.Project;
import roj.plugins.ci.Workspace;
import roj.plugins.ci.event.LibraryModifiedEvent;
import roj.text.TextUtil;
import roj.text.logging.Level;
import roj.ui.Argument;
import roj.ui.Terminal;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static roj.asmx.mapper.Mapper.DONT_LOAD_PREFIX;
import static roj.asmx.mapper.Mapper.MF_FIX_SUBIMPL;
import static roj.plugins.ci.FMD.*;
import static roj.ui.CommandNode.argument;
import static roj.ui.CommandNode.literal;

/**
 * @author Roj234
 * @since 2025/2/11 11:03
 */
public class MAP implements Processor {
	private int flag;

	@Subscribe
	public void onLibraryChange(LibraryModifiedEvent event) {
		FMD._lock();
		var owner = event.getOwner();
		if (owner == null) {
			for (var project : FMD.projects.values()) {
				project.mapper = null;
				project.mapperState = null;
				LOGGER.debug("库缓存失效 {}", project.getName());
			}
		} else {
			for (var project : FMD.projects.values()) {
				if (project == owner || project.getAllDependencies().contains(owner)) {
					project.mapper = null;
					project.mapperState = null;
					LOGGER.debug("库缓存失效 {}", project.getName());
				}
			}
		}
		FMD._unlock();
	}

	public MAP() {
		EVENT_BUS.register(this);
		FMD.MapPlugin = this;

		var finishNode = argument("文件", Argument.files(2)).executes(ctx -> {
			var workspace = ctx.argument("工作空间", Workspace.class);
			if (workspace == null && defaultProject != null) workspace = defaultProject.workspace;
			if (workspace == null || workspace.mapping == null) {
				Terminal.error("这个项目没有映射器");
				return;
			}

			List<File> files = Helpers.cast(ctx.argument("文件", List.class));

			var reverse = ctx.context.startsWith("unmap");
			Mapper m;
			if (reverse) {
				m = new Mapper(workspace.getInvMapper());
			} else {
				m = workspace.getMapper();
				m.flag = (byte) flag;
				m.loadLibraries(workspace.mappedDepend);
			}

			m.loadLibraries(files);
			m.packup();
			m.getSeperatedLibraries().clear();
			for (int i = 0; i < files.size(); i++) {
				File in = files.get(i);
				String postfix = reverse ? "-unmap" : "-mapped";
				if (IOUtil.fileName(in.getName()).endsWith(postfix)) {
					LOGGER.info("{}仅作为依赖，不映射", in.getName());
					continue;
				}
				File out = IOUtil.deriveOutput(in, postfix);
				try (var zfw = new ZipFileWriter(out)) {
					var classes = Context.fromZip(in, zfw);

					m.map(classes);
					m.getSeperatedLibraries().add(m.snapshot(null));

					for (int j = 0; j < classes.size(); j++) {
						var klass = classes.get(j);
						zfw.writeNamed(klass.getFileName(), klass.get());
					}
				}
				LOGGER.info("已处理{}", in);
			}
		});
		CommandManager.register(literal("map").then(argument("工作空间", Argument.oneOf(workspaces)).then(finishNode)).then(finishNode));
		CommandManager.register(literal("unmap").then(argument("工作空间", Argument.oneOf(workspaces)).then(finishNode)).then(finishNode));


		var mapName = argument("搜索词", Argument.string()).executes(ctx -> {
			var word = Pattern.compile(ctx.argument("搜索词", String.class));

			var workspace = ctx.argument("工作空间", Workspace.class);
			if (workspace == null && defaultProject != null) workspace = defaultProject.workspace;
			if (workspace == null || workspace.mapping == null) {
				Terminal.error("这个项目没有映射器");
				return;
			}

			Mapper m = workspace.getMapper();
			for (Map.Entry<Desc, String> entry : m.getMethodMap().entrySet()) {
				if (word.matcher(entry.getKey().owner+"."+entry.getKey().name+entry.getKey().param).find()) {
					System.out.println(entry.getKey()+" => "+entry.getValue());
				}
			}
			for (Map.Entry<Desc, String> entry : m.getFieldMap().entrySet()) {
				if (word.matcher(entry.getKey().owner+"."+entry.getKey().name).find()) {
					System.out.println(entry.getKey()+" => "+entry.getValue());
				}
			}
		});
		CommandManager.register(literal("mapname").then(argument("工作空间", Argument.oneOf(workspaces)).then(mapName)).then(mapName));
		CommandManager.register(literal("mapdebug").then(argument("type", Argument.string()).executes(ctx -> {
            var s = ctx.argument("type", String.class);
			int i = s.indexOf('#');
			Mapper.LOGGER.setLevel(Level.ALL);
			defaultProject.mapper.debugRelative(s.substring(0, i).replace('.', '/'), s.substring(i+1));
        })));
	}

	@Override public String name() {return "映射";}

	@Override public void init(CEntry config) {
		if (config.asMap().getBool("子类实现")) flag |= MF_FIX_SUBIMPL;
	}

	@Override public int beforeCompile(Compiler compiler, SimpleList<String> options, List<File> files, ProcessEnvironment ctx) {
		return ctx.project.mapperState == null && ctx.project.workspace.mapping != null ? 1 : 2;
	}

	@Override public synchronized List<Context> process(List<Context> classes, ProcessEnvironment ctx) {
		var project = ctx.project;
		if (project.workspace.mapping != null) {
			var mapper = getProjectMapper(project);

			var dependencies = project.getAllDependencies();

			List<Mapper.State> libs = mapper.getSeperatedLibraries();
			libs.clear();
			for (int i = 0; i < dependencies.size(); i++) {
				var state = dependencies.get(i).mapperState;
				if (state != null) libs.add(state);
			}

			var state = project.mapperState;
			if (state != null) {
				libs.add(state);
				mapper.mapIncr(classes);
			} else {
				mapper.map(classes);
			}
			project.mapperState = mapper.snapshot(state);
		}

		return classes;
	}

	public Mapper getProjectMapper(Project p) {
		var mapper = p.mapper;
		if (mapper != null) return mapper;

		var libraries = new SimpleList<File>();
		var digest = ILCrypto.SM3();
		Predicate<File> predicate = file -> {
			digest.update(IOUtil.getSharedByteBuf().putUTF(file.getName()).putLong(file.length()).putLong(file.lastModified()));

			String name = file.getName().toLowerCase(Locale.ROOT);
			return !name.startsWith(DONT_LOAD_PREFIX) && (name.endsWith(".zip") || name.endsWith(".jar"));
		};
		libraries.addAll(p.workspace.mappedDepend);
		IOUtil.findAllFiles(new File(BASE, "libs"), libraries, predicate);
		IOUtil.findAllFiles(new File(p.getRoot(), "libs"), libraries, predicate);

		LOGGER.trace("{}: 映射器的库列表：{}", p.getName(), libraries);

		Mapper m;
		var hash = digest.digest();
		try (var mzf = new ZipArchive(new File(DATA_PATH, ".mapCacheAll.zip"))) {
			m = new Mapper(p.workspace.getMapper());
			m.flag = (byte) flag;

			String entryId = TextUtil.bytes2hex(hash);
			var in = mzf.getStream(entryId);
			if (in != null) {
				try {
					m.loadCache(in, true);
					LOGGER.debug("{}: 从缓存恢复映射器", p.getName());
				} catch (Exception e) {
					LOGGER.warn("{}: 从缓存恢复映射器失败", e, p.getName());
				} finally {
					in.close();
				}
			}

			LOGGER.debug("{}: 重新构建映射器", p.getName());

			m.loadLibraries(libraries);

			ByteList buf = new ByteList();
			m.saveCache(buf, 2);

			int cacheWipe = mzf.entries().size()-config.getInt("映射缓存大小");
			if (cacheWipe > 0) {
				var entries = new SimpleList<>(mzf.entries());
				entries.sort((o1, o2) -> Long.compare(o1.getModificationTime(), o2.getModificationTime()));
				for (int i = 0; i < cacheWipe; i++) mzf.put(entries.get(i).getName(), null);
			}

			mzf.put(entryId, buf).flag = 0;
			mzf.save();
			buf._free();
		} catch (IOException e) {
			LOGGER.error("{}: 映射器构建失败", e, p.getName());
			return new Mapper();
		}

		p.mapper = m;
		return m.packup();
	}
}