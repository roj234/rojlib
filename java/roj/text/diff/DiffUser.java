package roj.text.diff;

import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.concurrent.TaskPool;
import roj.config.ConfigMaster;
import roj.config.Tokenizer;
import roj.config.Word;
import roj.config.auto.Serializer;
import roj.config.auto.Serializers;
import roj.io.IOUtil;
import roj.ui.CLIUtil;
import roj.ui.terminal.CommandContext;
import roj.ui.terminal.SimpleCliParser;

import java.io.File;
import java.util.List;

import static roj.text.diff.DiffResult.bar;
import static roj.ui.terminal.Argument.file;
import static roj.ui.terminal.CommandNode.argument;
import static roj.ui.terminal.SimpleCliParser.nullImpl;

/**
 * @author Roj234
 * @since 2023/8/2 0002 6:39
 */
public class DiffUser {
	public static void main(String[] args) throws Exception {
		CommandContext ctx = new SimpleCliParser()
			.add(argument("basePath", file(true))
					.then(argument("diffYml", file(false))
						.executes(nullImpl())))
			.parse(args, true);

		if (ctx == null) {
			System.out.println("DiffUser <basePath> <diffYml> [isText=true]");
			return;
		}

		basePath = ctx.argument("basePath", File.class);

		Serializer<List<DiffResult>> adapter = Serializers.SAFE.listOf(DiffResult.class);
		List<DiffResult> diffs = ConfigMaster.YAML.readObject(adapter, ctx.argument("diffYml", File.class));
		for (int i = diffs.size() - 1; i >= 0; i--) {
			DiffResult d = diffs.get(i);
			d.leftFile = new File(basePath, d.left);
			d.rightFile = new File(basePath, d.right);

			if(!d.leftFile.isFile() || !d.rightFile.isFile()) {
				diffs.remove(i);
				continue;
			}
			if (d.leftFile.getAbsolutePath().compareTo(d.rightFile.getAbsolutePath()) > 0) {
				File right = d.rightFile;
				d.rightFile = d.leftFile;
				d.leftFile = right;
			}

			bar.addMax(1);
			POOL.pushTask(() -> d.postProcess(true));
		}

		POOL.awaitFinish();

		bar.end("ok");
		bar.reset();
		diffs.sort((o1, o2) -> Integer.compare(o1.diff, o2.diff));
		for (int i = diffs.size() - 1; i > 0; i--) {
			DiffResult d = diffs.get(i);
			if ((double) d.diff / d.minSize > 0.75) {
				System.out.println("too similar:"+d);
				diffs.remove(i);
			}
			else if (d.equals(diffs.get(i - 1))) {
				System.out.println("same:"+d);
				diffs.remove(i);
			}
		}
		System.out.println("count:" + diffs.size());
		ConfigMaster.YAML.writeObject(diffs, adapter, ctx.argument("diffYml", File.class));

		bar.addMax(diffs.size());
		for (int i = 0; i < diffs.size(); i++) {
			DiffResult d = diffs.get(i);

			List<String> args2 = new SimpleList<>();
			String cmd = "D:\\Desktop\\nv\\WinMerge\\WinMergeU.exe /e /t Text /xq /u /fl /enableexitcode /al /ignorecodepage /cp 54936";
			Tokenizer l = Tokenizer.arguments().init(cmd);
			while (l.hasNext()) {
				Word w = l.next();
				if (w.type() == Word.EOF) break;
				args2.add(w.val());
			}
			args2.add(d.leftFile.getAbsolutePath());
			args2.add(d.rightFile.getAbsolutePath());

			bar.addCurrent(1);

			new ProcessBuilder().command("D:\\Everything\\Everything.exe", "-s", "<" + Tokenizer.addSlashes(IOUtil.fileName(d.leftFile.getName())) + ">|<" + Tokenizer.addSlashes(IOUtil.fileName(d.rightFile.getName())) + '>').start();
			int exit = new ProcessBuilder().command(args2).start().waitFor();

			if (exit == 0) {
				System.out.println("left:" + d.left);
				System.out.println("right:" + d.right);
				System.out.println("删除左(l)右(r)取消(c)");
				char c = CLIUtil.awaitCharacter(MyBitSet.from("lrc"));
				if (c != 'c') {
					if (c == 'l') {
						d.leftFile.delete();
					} else if (c == 'r') {
						d.rightFile.delete();
					} else {
						System.exit(1);
					}
				}
			}

			System.out.println();
		}
	}

	private static File basePath;
	private static final TaskPool POOL = TaskPool.MaxSize(9999, "TDD worker");
}