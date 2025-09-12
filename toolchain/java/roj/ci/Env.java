package roj.ci;

import roj.annotation.Comment;
import roj.collect.LinkedHashMap;
import roj.collect.TrieTreeSet;
import roj.config.mapper.Optional;
import roj.util.ArtifactVersion;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2025/08/29 21:27
 */
@Optional(write = Optional.WriteMode.ALWAYS)
final class Env {
	static final TrieTreeSet EMPTY_SET = new TrieTreeSet();
	static final LinkedHashMap<String, String> EMPTY_MAP = new LinkedHashMap<>();

	@Comment("工作空间列表，每个工作空间是一个物理目录、一些固定依赖项、预处理器和预定义变量的集合，通过workspace指令修改")
	List<Workspace> workspaces = Collections.emptyList();
	List<Project> projects = Collections.emptyList();

	@Comment("默认项目名称，当未指定项目名称时使用，通过project setdefault修改")
	String default_project;
	@Comment("会自动编译的项目，通过project auto修改")
	List<String> auto_compile = Collections.emptyList();

	static final class Project implements Cloneable {
		static final ArtifactVersion DEFAULT_VERSION = new ArtifactVersion("1");

		String name;
		@Optional(nullValue = "roj.ci.Env.Type.PROJECT")
		@Comment("项目类型：PROJECT(可独立构建), MODULE(子模块), ARTIFACT(仅组合产物)")
		Type type = Type.PROJECT;
		@Optional(nullValue = "roj.ci.Env.Project.DEFAULT_VERSION")
		ArtifactVersion version = DEFAULT_VERSION;
		@Optional(nullValue = "java.nio.charset.StandardCharsets.UTF_8")
		Charset charset = StandardCharsets.UTF_8;
		@Optional(write = Optional.WriteMode.NON_BLANK)
		List<String> compiler_options = Collections.emptyList();
		@Deprecated @Optional boolean compiler_options_overwrite;
		String workspace;
		@Optional(write = Optional.WriteMode.NON_BLANK)
		@Comment("项目依赖，键为依赖标识符，值为作用域")
		Map<String, Dependency.Scope> dependency = Collections.emptyMap();
		@Optional(write = Optional.WriteMode.NON_BLANK)
		@Comment("自定义变量，可用于自定义配置\n也用来进行模板替换")
		LinkedHashMap<String, String> variables = EMPTY_MAP;
		@Optional(write = Optional.WriteMode.NON_BLANK)
		@Comment("需要进行变量替换的文件路径模式集合")
		TrieTreeSet variable_replace_in = EMPTY_SET;
		@Optional(write = Optional.WriteMode.NON_BLANK)
		@Comment("打包时需要忽略的文件路径模式集合")
		TrieTreeSet bundle_ignore = EMPTY_SET;
	}

	enum Type {
		/**
		 * 可独立构建的项目
		 */
		PROJECT,
		/**
		 * 不可单独构建、必须作为其他项目一部分的子模块
		 */
		MODULE,
		/**
		 * 本身没有源代码需要编译，仅用于组合PROJECT或MODULE生成自定义Artifact使用
		 */
		ARTIFACT;

		boolean canBuild() {return this != MODULE;}
		boolean hasFile() {return this != ARTIFACT;}
	}
}
