package roj.reflect;

import roj.ui.terminal.Argument;
import roj.ui.terminal.CommandContext;
import roj.ui.terminal.CommandNode;
import roj.ui.terminal.SimpleCliParser;

import java.lang.reflect.Field;

/**
 * @author Roj234
 * @since 2024/1/21 0021 23:19
 */
public class ModuleKiller {
	public static void main(String[] args) throws Exception {
		CommandContext kill = new SimpleCliParser().add(CommandNode.argument("is_kill", Argument.bool()).executes(SimpleCliParser.nullImpl())).parse(args, true);
		if (kill.argument("is_kill", Boolean.class)) KillModuleSince();

		Field name = Class.class.getDeclaredField("name");
		name.setAccessible(true);
		name.set(Object.class, "安全管理器，我的安全管理器，你死得好惨啊！！！");

		System.out.println(Object.class.getName());
	}

	public static void KillModuleSince() {
		Module module = Object.class.getModule();
		for (Module module_ : module.getLayer().modules()) {
			for (String package_ : module_.getDescriptor().packages()) {
				VMInternals.OpenModule(module_, package_, ModuleKiller.class.getModule());
			}
		}
	}
}