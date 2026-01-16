package roj.ci.plugin;

import roj.archive.zip.ZipEditor;
import roj.archive.zip.ZipEntry;
import roj.archive.zip.ZipPacker;
import roj.asm.MemberDescriptor;
import roj.asmx.Context;
import roj.asmx.mapper.Mapper;
import roj.ci.*;
import roj.ci.event.LibraryModifiedEvent;
import roj.ci.minecraft.MappingUI;
import roj.collect.ArrayList;
import roj.config.node.ConfigValue;
import roj.crypt.CryptoFactory;
import roj.event.Subscribe;
import roj.io.IOUtil;
import roj.text.TextUtil;
import roj.text.logging.Level;
import roj.ui.Argument;
import roj.ui.Tty;
import roj.util.ByteList;
import roj.util.Helpers;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;

import static roj.asmx.mapper.Mapper.DONT_LOAD_PREFIX;
import static roj.asmx.mapper.Mapper.MF_FIX_SUBIMPL;
import static roj.ci.MCMake.*;
import static roj.ui.CommandNode.argument;
import static roj.ui.CommandNode.literal;

/**
 * @author Roj234
 * @since 2025/2/11 11:03
 */
public class MAP implements Plugin {
	private int flag;

	@Subscribe
	public void onLibraryChange(LibraryModifiedEvent event) {
		MCMake._lock(false);
		var owner = event.getOwner();
		if (owner == null) {
			for (var project : MCMake.projects.values()) {
				project.mapper = null;
				project.mapperState = null;
				log.debug("库缓存失效 {}", project.getName());
			}
		} else {
			for (var project : MCMake.projects.values()) {
				if (project == owner || project.getProjectDependencies().contains(owner)) {
					project.mapper = null;
					project.mapperState = null;
					log.debug("库缓存失效 {}", project.getName());
				}
			}
		}
		MCMake._unlock();
	}

	public MAP() {
		EVENT_BUS.register(this);

		var finishNode = argument("文件", Argument.files(2)).executes(ctx -> {
			var workspace = ctx.argument("工作空间", Workspace.class);
			if (workspace == null && defaultProject != null) workspace = defaultProject.workspace;
			if (workspace == null || workspace.mapping == null) {
				Tty.error("这个项目没有映射器");
				return;
			}

			List<File> files = Helpers.cast(ctx.argument("文件", List.class));

			var reverse = ctx.context.startsWith("unmap");
			Mapper m;
			if (reverse) {
				m = new Mapper(workspace.getInverseMapping());
			} else {
				m = new Mapper(workspace.getMapping());
				m.flag = (byte) flag;
				m.loadLibraries(workspace.getMappedDepend());
			}

			m.loadLibraries(files);
			m.packup();
			m.getSeperatedLibraries().clear();
			for (int i = 0; i < files.size(); i++) {
				File in = files.get(i);
				String postfix = reverse ? "-unmap" : "-mapped";
				if (IOUtil.getBaseName(in.getName()).endsWith(postfix)) {
					log.info("{}仅作为依赖，不映射", in.getName());
					continue;
				}
				File out = IOUtil.addSuffix(in, postfix);
				try (var zfw = new ZipPacker(out)) {
					var classes = Context.fromZip(in, zfw);

					m.map(classes);
					m.getSeperatedLibraries().add(m.snapshot(null));

					for (int j = 0; j < classes.size(); j++) {
						var klass = classes.get(j);
						zfw.writeNamed(klass.getFileName(), klass.getClassBytes());
					}
				}
				log.info("已处理{}", in);
			}
		});

		var mapName = argument("搜索词", Argument.string()).executes(ctx -> {
			var word = Pattern.compile(ctx.argument("搜索词", String.class));

			var workspace = ctx.argument("工作空间", Workspace.class);
			if (workspace == null && defaultProject != null) workspace = defaultProject.workspace;
			if (workspace == null || workspace.mapping == null) {
				Tty.error("这个项目没有映射器");
				return;
			}

			var m = workspace.getMapping();
			for (Map.Entry<MemberDescriptor, String> entry : m.getMethodMap().entrySet()) {
				MemberDescriptor key = entry.getKey();
				if (word.matcher(key.owner+"."+key.name+key.rawDesc).find()) {
					System.out.println(key.owner+" "+key.name+key.rawDesc+" # "+entry.getValue());
				}
			}
			for (Map.Entry<MemberDescriptor, String> entry : m.getFieldMap().entrySet()) {
				MemberDescriptor key = entry.getKey();
				if (word.matcher(key.owner+"."+key.name).find()) {
					System.out.println(key.owner+" "+key.name+" # "+entry.getValue());
				}
			}
		});

		COMMANDS.register(literal("map")
				.then(literal("create").executes(ctx -> {
					JFrame frame = new MappingUI();
					frame.pack();
					frame.setResizable(false);
					frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
					frame.setVisible(true);
				}))
				.then(literal("debug").then(argument("type", Argument.string()).executes(ctx -> {
					var s = ctx.argument("type", String.class);
					int i = s.indexOf('#');
					Mapper.LOGGER.setLevel(Level.ALL);
					defaultProject.mapper.debugRelative(s.substring(0, i).replace('.', '/'), s.substring(i+1));
				})))
				.then(argument("工作空间", Argument.oneOf(workspaces)).then(finishNode).then(mapName))
				.then(finishNode).then(mapName)
		);
		COMMANDS.register(literal("unmap")
				.then(argument("工作空间", Argument.oneOf(workspaces)).then(finishNode))
				.then(finishNode)
		);
	}

	@Override public String name() {return "映射";}

	@Override public void init(ConfigValue config) {
		if (config.asMap().getBool("子类实现")) flag |= MF_FIX_SUBIMPL;
	}

	@Override public boolean preProcess(ArrayList<String> options, List<File> sources, BuildContext ctx) {
		return ctx.project.mapperState == null && ctx.project.workspace.mapping != null;
	}

	@Override public synchronized void process(BuildContext ctx) {
		var project = ctx.project;
		if (project.workspace.mapping != null) {
			var mapper = getProjectMapper(project);

			var dependencies = project.getProjectDependencies();

			List<Mapper.State> libs = mapper.getSeperatedLibraries();
			libs.clear();
			for (int i = 0; i < dependencies.size(); i++) {
				var state = dependencies.get(i).mapperState;
				if (state != null) libs.add(state);
			}

			var state = project.mapperState;
			if (state != null) {
				libs.add(state);
				mapper.mapIncr(ctx.getChangedClasses());
			} else {
				mapper.map(ctx.getChangedClasses());
			}
			project.mapperState = mapper.snapshot(state);
		}
	}

	public Mapper getProjectMapper(Project p) {
		var mapper = p.mapper;
		if (mapper != null) return mapper;

		var libraries = new ArrayList<File>();
		var digest = CryptoFactory.SM3();
		BiPredicate<String, BasicFileAttributes> callback = (pathname, attr) -> {
			String name = IOUtil.getName(pathname);
			digest.update(IOUtil.getSharedByteBuf().putUTF(name).putLong(attr.size()).putLong(attr.lastModifiedTime().toMillis()));

			name = name.toLowerCase(Locale.ROOT);
			return !name.startsWith(DONT_LOAD_PREFIX) && (name.endsWith(".zip") || name.endsWith(".jar"));
		};
		libraries.addAll(p.workspace.getMappedDepend());
		IOUtil.listFiles(new File(BASE, "lib"), libraries, callback);
		for (Dependency dep : p.getCompileDependencies()) {
			dep.forEachJar(f -> {
				try {
					if (callback.test(f.getAbsolutePath(), Files.readAttributes(f.toPath(), BasicFileAttributes.class)))
						libraries.add(f);
				} catch (IOException e) {
					Helpers.athrow(e);
				}
			});
		}

		log.trace("{}: 映射器的库列表：{}", p.getName(), libraries);

		Mapper m;
		var hash = digest.digest();
		loaded:
		try (var mzf = new ZipEditor(new File(CACHE_PATH, "mapper_cache.zip"))) {
			m = new Mapper(p.workspace.getMapping());
			m.flag = (byte) flag;

			String entryId = TextUtil.bytes2hex(hash);
			var in = mzf.getInputStream(entryId);
			if (in != null) {
				try {
					m.loadCache(in, true);
					log.debug("{}: 从缓存恢复映射器 {}", p.getName(), entryId);
					break loaded;
				} catch (Exception e) {
					log.warn("{}: 从缓存恢复映射器失败", e, p.getName());
				} finally {
					in.close();
				}
			}

			log.debug("{}: 重新构建映射器", p.getName());

			m.loadLibraries(libraries);

			ByteList buf = new ByteList();
			m.saveCache(buf, 2);

			int cacheWipe = mzf.entries().size()-config.getInt("映射缓存大小");
			if (cacheWipe > 0) {
				var entries = new ArrayList<>(mzf.entries());
				entries.sort((o1, o2) -> Long.compare(o1.getModificationTime(), o2.getModificationTime()));
				for (int i = 0; i < cacheWipe; i++) mzf.put(entries.get(i).getName(), null);
			}

			mzf.put(entryId, buf).setMethod(ZipEntry.STORED);
			mzf.save();
			buf.release();
		} catch (IOException e) {
			log.error("{}: 映射器构建失败", e, p.getName());
			return new Mapper();
		}

		p.mapper = m;
		return m.packup();
	}
}