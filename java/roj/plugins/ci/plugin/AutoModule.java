package roj.plugins.ci.plugin;

import roj.asm.AsmCache;
import roj.asm.ClassNode;
import roj.asm.Opcodes;
import roj.asm.attr.Attribute;
import roj.asm.attr.ClassListAttribute;
import roj.asm.attr.ModuleAttribute;
import roj.asm.attr.StringAttribute;
import roj.asmx.Context;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.text.TextUtil;
import roj.util.DynByteBuf;

import java.util.List;

/**
 * @author solo6975
 * @since 2022/5/22 1:58
 */
public class AutoModule implements Processor {
	@Override
	public String name() {return "自动模块化";}

	@Override
	public List<Context> process(List<Context> classes, ProcessEnvironment ctx) {
		if (ctx.increment == 0 && "true".equals(ctx.project.getVariables().get("fmd:auto_module:enable"))) {
			var moduleName = ctx.project.getVariables().getOrDefault("fmd:auto_module:name", ctx.project.getName());
			MyHashSet<String> packages = new MyHashSet<>();

			for (Context context : classes) {
				String className = context.getClassName();
				if (className.startsWith("module-info")) return classes;

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

			moduleInfo.addAttribute(new ClassListAttribute(Attribute.ModulePackages, new SimpleList<>(packages)));
			moduleInfo.addAttribute(moduleAttr);

			ctx.generatedFiles.put("module-info.class", DynByteBuf.wrap(AsmCache.toByteArray(moduleInfo)));
		}
		return classes;
	}
}