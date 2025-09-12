package roj.ci.plugin;

import roj.archive.zip.ZipArchive;
import roj.asm.AsmCache;
import roj.asm.ClassNode;
import roj.asm.Opcodes;
import roj.asm.attr.Attribute;
import roj.asm.attr.ClassListAttribute;
import roj.asm.attr.ModuleAttribute;
import roj.asm.attr.StringAttribute;
import roj.asmx.Context;
import roj.ci.MCMake;
import roj.collect.ArrayList;
import roj.collect.HashSet;
import roj.text.TextUtil;
import roj.ui.Argument;
import roj.ui.TUI;
import roj.util.DynByteBuf;

import java.io.File;

import static roj.ui.CommandNode.argument;
import static roj.ui.CommandNode.literal;

/**
 * @author Roj234
 * @since 2025/3/22 7:59
 */
public class AutoModule implements Processor {
	static {
		MCMake.COMMANDS.register(literal("automodule").then(argument("文件", Argument.file()).then(argument("模块名", Argument.string()).executes(ctx -> {
			File file = ctx.argument("文件", File.class);
			var moduleName = ctx.argument("模块名", String.class);

			HashSet<String> packages = new HashSet<>();

			boolean hasModule = false;
			for (Context context : Context.fromZip(file, null)) {
				String className = context.getClassName();
				if (className.startsWith("module-info")) {
					hasModule = true;
					continue;
				}
				int i = className.lastIndexOf('/');
				if (i < 0) throw new UnsupportedOperationException();
				String pack = className.substring(0, i);
				packages.add(pack);
			}

			if (hasModule) {
				char c1 = TUI.key("YyNn", "该文件已包含模块信息，是否替换 [Yn]");
				if (c1 != 'y' && c1 != 'Y') return;
			}

			var moduleInfo = new ClassNode();
			var moduleAttr = new ModuleAttribute(moduleName, 0);

			moduleInfo.version = ClassNode.JavaVersion(17);
			moduleAttr.self.version = "1.0.0";
			moduleAttr.requires.add(new ModuleAttribute.Module("java.base", Opcodes.ACC_MANDATED));
			for (String pack : packages) {
				moduleAttr.exports.add(new ModuleAttribute.Export(pack));
			}

			moduleInfo.name("module-info");
			moduleInfo.parent(null);
			moduleInfo.modifier = Opcodes.ACC_MODULE;
			moduleInfo.addAttribute(new StringAttribute(Attribute.SourceFile, "module-info.java"));
			//moduleInfo.putAttr(new AttrString(Attribute.ModuleTarget, "windows-amd64"));
			//moduleInfo.putAttr(new AttrClassList(Attribute.ModulePackages, new ArrayList<>(packages)));
			moduleInfo.addAttribute(moduleAttr);

			System.out.println(moduleAttr);
			try (var za = new ZipArchive(file)) {
				za.put("module-info.class", DynByteBuf.wrap(AsmCache.toByteArray(moduleInfo)));
				za.save();
			}
			System.out.println("IL自动模块，应用成功！");
		}))));
	}

	@Override
	public String name() {return "自动模块化";}

	@Override
	public void afterCompile(BuildContext ctx) {
		if (ctx.increment > BuildContext.INC_REBUILD) return;

		var moduleName = ctx.project.getVariables().getOrDefault("fmd:auto_module:name", ctx.project.getName());
		if (!moduleName.isEmpty()) {
			HashSet<String> packages = new HashSet<>();

			for (Context context : ctx.getClasses()) {
				String className = context.getClassName();
				if (className.startsWith("module-info")) return;

				int i = className.lastIndexOf('/');
				if (i < 0) throw new UnsupportedOperationException();
				String pack = className.substring(0, i);
				packages.add(pack);
			}

			var moduleInfo = new ClassNode();
			moduleInfo.name("module-info");
			moduleInfo.parent(null);
			moduleInfo.version = ClassNode.JavaVersion(17);
			moduleInfo.modifier = Opcodes.ACC_MODULE;

			var moduleAttr = new ModuleAttribute(moduleName, 0);
			moduleAttr.self.version = ctx.project.variables.get("version");

			moduleAttr.requires.add(new ModuleAttribute.Module("java.base", Opcodes.ACC_MANDATED));
			var importModules = TextUtil.split(ctx.project.getVariables().getOrDefault("fmd:auto_module:import", ""), ' ');
			for (String importModule : importModules) moduleAttr.requires.add(new ModuleAttribute.Module(importModule, 0));

			for (String pack : packages) moduleAttr.exports.add(new ModuleAttribute.Export(pack));

			moduleInfo.addAttribute(new StringAttribute(Attribute.SourceFile, "MCMakeAutoModule"));
			var mainClass = ctx.project.getVariables().get("fmd:auto_module:main");
			if (mainClass != null) moduleInfo.addAttribute(new StringAttribute(Attribute.ModuleMainClass, mainClass.replace('.', '/')));

			moduleInfo.addAttribute(new ClassListAttribute(Attribute.ModulePackages, new ArrayList<>(packages)));
			moduleInfo.addAttribute(moduleAttr);

			ctx.addFile("module-info.class", DynByteBuf.wrap(AsmCache.toByteArray(moduleInfo)));
		}
	}
}