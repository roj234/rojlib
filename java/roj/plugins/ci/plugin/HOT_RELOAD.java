package roj.plugins.ci.plugin;

import roj.asm.tree.ConstantData;
import roj.asm.util.Context;
import roj.collect.SimpleList;
import roj.config.Tokenizer;
import roj.config.data.CEntry;
import roj.io.IOUtil;
import roj.plugins.ci.FMD;
import roj.plugins.ci.HRServer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Roj234
 * @since 2025/2/11 11:03
 */
public class HOT_RELOAD implements Processor {
	private HRServer server;

	@Override public String name() {return "热重载";}

	@Override public void init(CEntry config) {
		IOUtil.closeSilently(server);
		try {
			server = new HRServer(config.asInt());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		FMD.LOGGER.info("热重载启动成功，在任意JVM中加入该参数即可使用");
		FMD.LOGGER.info("  -javaagent:\""+Tokenizer.addSlashes(new File(FMD.BASE, "bin/Agent.jar").getAbsolutePath())+"\"="+config.asInt());
	}

	@Override public synchronized List<Context> process(List<Context> classes, ProcessEnvironment ctx) {
		if (ctx.changedClassIndex > 0) {
			List<ConstantData> classData = new SimpleList<>(ctx.changedClassIndex);
			for (int i = 0; i < ctx.changedClassIndex; i++) {
				classData.add(classes.get(i).getData());
			}
			server.sendChanges(classData);
		}
		return classes;
	}
}