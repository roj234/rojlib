package roj.ci.plugin;

import roj.asm.FieldNode;
import roj.asm.Opcodes;
import roj.asmx.Context;

import java.util.List;

/**
 * @author Roj234
 * @since 2025/09/03 4:20
 */
public class AdvanceCompress implements Processor {
	@Override
	public String name() {return "删除私有静态常量";}

	@Override
	public void afterCompile(ProcessEnvironment ctx) {
		List<Context> classes = ctx.getClasses();
		for (int i = 0; i < classes.size(); i++) {
			Context ctx1 = classes.get(i);
			List<FieldNode> fields = ctx1.getData().fields();
			for (int j = fields.size() - 1; j >= 0; j--) {
				FieldNode field = fields.get(j);
				if (field.getAttribute("ConstantValue") != null && (field.modifier&Opcodes.ACC_STATIC) != 0) {
					fields.remove(j);
				}
			}
		}
	}
}