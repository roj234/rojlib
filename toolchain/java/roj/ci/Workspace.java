package roj.ci;

import org.jetbrains.annotations.Nullable;
import roj.asmx.mapper.Mapper;
import roj.ci.event.ProjectUpdateEvent;
import roj.ci.plugin.Processor;
import roj.collect.ArrayList;
import roj.collect.LinkedHashMap;
import roj.collect.TrieTreeSet;
import roj.config.XmlParser;
import roj.config.mapper.Optional;
import roj.config.node.MapValue;
import roj.config.node.xml.Document;
import roj.config.node.xml.Node;
import roj.io.IOUtil;
import roj.reflect.Bypass;
import roj.text.*;
import roj.util.function.Flow;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2025/2/12 7:02
 */
public class Workspace {
	public interface Factory { Workspace build(MapValue config); }
	public static Factory factory(Class<?> type) {return Bypass.builder(Factory.class).delegate(type, "build").build();}

	public String type;
	public String id;
	@Optional String path = "projects";
	public List<File> depend, mappedDepend, unmappedDepend;
	@Optional public List<String> processors = Collections.emptyList();

	@Nullable public File mapping;
	private transient Mapper mapper, invMapper;

	@Optional public LinkedHashMap<String, String> variables = Env.EMPTY_MAP;
	@Optional public TrieTreeSet variable_replace_in = Env.EMPTY_SET;

	private transient Workspace _next;

	private transient List<Processor> processorsImp;

	public List<Processor> getProcessors() {
		if (processorsImp == null) {
			processorsImp = Flow.of(MCMake.processors).filter(processor -> processors.contains(processor.getClass().getName())).toList();
		}
		return processorsImp;
	}

	private transient File projectPath;
	File getProjectPath() {
		if (projectPath == null) {
			projectPath = new File(MCMake.BASE, path == null ? id : path);
			projectPath.mkdir();
		}
		return projectPath;
	}

	public Mapper getMapper() throws IOException {
		var m = mapper;
		if (m == null) {
			m = new Mapper();
			if (mapping != null) {
				try (var fs = new FileInputStream(mapping)) {
					m.loadCache(fs, false);
				}
			}
			mapper = m;
		}
		return m;
	}
	public Mapper getInvMapper() throws IOException {
		var m = invMapper;
		if (m == null) {
			m = new Mapper(getMapper());
			m.reverseSelf();
			m.loadLibraries(unmappedDepend);
			m.flag = 0;
			m.packup();
			invMapper = m;
		}
		return m;
	}

	public void registerLibrary() {
		if (processors == Collections.EMPTY_LIST)
			processors = new ArrayList<>();
		for (Processor processor : MCMake.processors) {
			if (processor.defaultEnabled()) {
				String name = processor.getClass().getName();
				if (!processors.contains(name)) {
					processors.add(name);
				}
			}
		}

		File file = new File(getProjectPath(), ".idea/libraries/"+URICoder.escapeFileName(id)+".xml");

		var doc = new Document();
		doc.headless();

		var component = doc.createElement("component").attr("name", "libraryTable");
		doc.add(component);

		var library = doc.createElement("library").attr("name", getLibraryId());
		component.add(library);

		var classes = doc.createElement("CLASSES");
		library.add(classes);
		library.add(doc.createElement("JAVADOC").voidElement());
		library.add(doc.createElement("SOURCES").voidElement());

		for (File file1 : mappedDepend) {
			classes.add(doc.createElement("root").attr("url", "jar://$PROJECT_DIR$/../cache/"+file1.getName()+"!/").voidElement());
		}

		for (File file1 : depend) {
			classes.add(doc.createElement("root").attr("url", "jar://$PROJECT_DIR$/../cache/"+file1.getName()+"!/").voidElement());
		}

		try (var sout = TextWriter.to(file, StandardCharsets.UTF_8)) {
			component.toCompatXML(sout);
		} catch (IOException e) {
			MCMake.LOGGER.error("registerLibrary", e);
		}
	}
	public void unregisterLibrary() {
		File file = new File(getProjectPath(), ".idea/libraries/"+URICoder.escapeFileName(id)+".xml");
		file.delete();
	}
	public String getLibraryId() {
		return "MCMake工作空间-"+id;
	}

	/**
	 * mode = 0 add
	 * mode = 1 del
	 * mode = 2 regenerate
	 */
	public static void registerModule(Project p, int mode) throws IOException {
		if (!p.conf.type.hasFile()) return;
		File ipr = new File(p.workspace.getProjectPath(), ".idea/modules.xml");

		var name = p.getShortName();

		Document document;
		try {
			document = XmlParser.parses(IOUtil.readString(ipr));
		} catch (ParseException e) {
			throw new RuntimeException("XML格式错误", e);
		}
		var modules = document.querySelector("/project/component[name=ProjectModuleManager]/modules").asElement();
		boolean existInProject = false;
		needSave: {
			List<Node> children = modules.children();
			for (int i = 0; i < children.size(); i++) {
				Node child = children.get(i);
				if (child.nodeType() == Node.ELEMENT) {
					var filepath = child.asElement().attr("filepath").asString();
					if (filepath.equals("$PROJECT_DIR$/"+p.getName()+"/"+p.getShortName()+".iml")) {
						if (mode == 1) children.remove(i);
						existInProject = true;
						break needSave;
					}
				}
			}
			if (mode != 0) return;
		}

		var iml = new File(p.root, name+".iml");
		if (iml.isFile() && mode == 0) return;

		if (mode == 1) {
			Files.delete(iml.toPath());
		} else {
			CharList sb = generateIMLForProject(p);
			try (var tw = TextWriter.to(iml, StandardCharsets.UTF_8)) {
				tw.append(sb);
			}

			if (existInProject) return;

			var module = document.createElement("module");
			module.isVoid = true;
			module.attr("fileurl", "file://$PROJECT_DIR$/"+p.getName()+"/"+p.getShortName()+".iml");
			module.attr("filepath", "$PROJECT_DIR$/"+p.getName()+"/"+p.getShortName()+".iml");
			modules.add(module);
		}

		IOUtil.writeFileEvenMoreSafe(ipr.getParentFile(), ipr.getName(), file -> {
			try (var tw = TextWriter.to(file, StandardCharsets.UTF_8)) {
				document.toXML(tw);
			}
		});
	}

	public static CharList generateIMLForProject(Project p) {
		var sb = new CharList("""
				<?xml version="1.0" encoding="UTF-8"?><!--MCMake-->
				<module type="JAVA_MODULE" version="4">
				  <component name="NewModuleRootManager" inherit-compiler-output="true">
				    <exclude-output />
				    <content url="file://$MODULE_DIR$">
				      <sourceFolder url="file://$MODULE_DIR$/java" isTestSource="false" />
				""");

		if (p.resPath.isDirectory()) {
			sb.append("<sourceFolder url=\"file://$MODULE_DIR$/resources\" type=\"java-resource\" />");
		}
		sb.append("</content>\n<orderEntry type=\"inheritedJdk\" />");

		CharList dynlib = new CharList();
		MCMake.EVENT_BUS.post(new ProjectUpdateEvent(p, dynlib));

		if (dynlib.length() > 0) {
			sb.append("<orderEntry type=\"module-library\"><library><CLASSES>").append(dynlib).append(
					"""
					    </CLASSES>
					    <JAVADOC />
					    <SOURCES />
					  </library>
					</orderEntry>
					""");
		}

		var workspace = p.workspace;
		if (workspace.mappedDepend.size() + workspace.depend.size() > 0) {
			Tokenizer.escape(sb.append("<orderEntry type=\"library\" name=\""), p.workspace.getLibraryId()).append("\" level=\"project\" />\n");
		}

		if (p.libPath.isDirectory()) {
			sb.append("""
					<orderEntry type="module-library">
					  <library>
					    <CLASSES>
					      <root url="file://$MODULE_DIR$/lib" />
					    </CLASSES>
					    <JAVADOC />
					    <SOURCES />
					    <jarDirectory url="file://$MODULE_DIR$/lib" recursive="true" />
					  </library>
					</orderEntry>
					""");
		}

		sb.append("""
				<orderEntry type="library" name="MCMake公共依赖" level="project" />
				<orderEntry type="sourceFolder" forTests="false" />
				""");

		for (Map.Entry<String, Dependency.Scope> entry : p.conf.dependency.entrySet()) {
			var dependency = MCMake.projects.get(entry.getKey());
			if (dependency != null) {
				sb.append("<orderEntry type=\"module\" module-name=\"").append(dependency.getShortName());
			} else {
				// FIXME other libraries
				continue;
			}

			switch (entry.getValue()) {
				case COMPILE -> sb.append("\" />\n");
				case EXPORT -> sb.append("\" exported=\"\" />\n");
				case BUNDLED -> sb.append("\" scope=\"PROVIDED\" />\n");
			}
		}

		return sb.append("</component></module>");
	}
}
