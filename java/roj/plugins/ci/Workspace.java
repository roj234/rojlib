package roj.plugins.ci;

import org.jetbrains.annotations.Nullable;
import roj.asmx.mapper.Mapper;
import roj.config.ParseException;
import roj.config.XMLParser;
import roj.config.auto.Optional;
import roj.config.data.CMap;
import roj.config.data.Document;
import roj.config.data.Node;
import roj.io.IOUtil;
import roj.plugins.ci.event.ProjectUpdateEvent;
import roj.reflect.Bypass;
import roj.text.CharList;
import roj.text.TextReader;
import roj.text.TextWriter;

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
	public interface Factory { Workspace build(CMap config); }
	public static Factory factory(Class<?> type) {return Bypass.builder(Factory.class).delegate(type, "build").build();}

	public String type;
	public String id;
	public List<File> depend, mappedDepend, unmappedDepend;

	@Nullable public File mapping;
	private transient Mapper mapper, invMapper;

	@Optional public Map<String, String> variables = Collections.emptyMap();

	private transient Workspace _next;

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

	public static void addIDEAProject(Project p, boolean delete) throws IOException {
		File ipr = null;
		for (File file : FMD.BASE.listFiles()) {
			if (file.getName().endsWith(".ipr")) {
				if (ipr != null) throw new IllegalStateException("项目根目录找到多个ipr文件！");
				ipr = file;
			}
		}
		var name = p.getName();

		Document document;
		try {
			document = XMLParser.parses(IOUtil.readString(ipr));
		} catch (ParseException e) {
			throw new RuntimeException("XML格式错误", e);
		}
		var modules = document.querySelector("/project/component[name=ProjectModuleManager]/modules").asElement();
		boolean exist = false;
		needSave: {
			List<Node> children = modules.children();
			for (int i = 0; i < children.size(); i++) {
				Node child = children.get(i);
				if (child.nodeType() == Node.ELEMENT) {
					var filepath = child.asElement().attr("filepath").asString();
					if (filepath.equals("$PROJECT_DIR$/projects/"+name+".iml")) {
						if (delete) children.remove(i);
						exist = true;
						break needSave;
					}
				}
			}
			if (delete) return;
		}

		var iml = new File(FMD.PROJECT_PATH, name+".iml");

		if (!delete) {
			var module = document.createElement("module");
			module.shortTag = true;
			module.attr("fileurl", "file://$PROJECT_DIR$/projects/"+name+".iml");
			module.attr("filepath", "$PROJECT_DIR$/projects/"+name+".iml");
			modules.add(module);

			CharList module_workspace = new CharList();
			var workspace = p.workspace;

			var event = new ProjectUpdateEvent(p, module_workspace);

			FMD.EVENT_BUS.post(event);
			for (File file : workspace.mappedDepend) {
				event.add(file.getName());
			}
			for (File file : workspace.depend) {
				event.add(file.getName());
			}

			CharList sb;
			File f = new File(FMD.DATA_PATH, "template.iml");
			try (var r = TextReader.auto(f)) {
				sb = IOUtil.getSharedCharBuf().readFully(r)
						   .replace("${module_name}", name)
						   .replace("${module_workspace}", module_workspace);
			}
			try (var tw = TextWriter.to(iml, StandardCharsets.UTF_8)) {
				tw.append(sb);
			}

			if (exist) return;
		} else {
			//Files.delete(iml.toPath());
		}

		var tmp = new File(ipr.getAbsolutePath()+".tmp");
		try (var tw = TextWriter.to(tmp, StandardCharsets.UTF_8)) {
			document.toXML(tw, 0);
			tw.flush();
			Files.delete(ipr.toPath());
			tmp.renameTo(ipr);
		}
	}
}
