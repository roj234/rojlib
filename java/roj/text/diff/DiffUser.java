package roj.text.diff;

import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.concurrent.TaskPool;
import roj.config.ConfigMaster;
import roj.config.Tokenizer;
import roj.config.Word;
import roj.config.auto.Serializer;
import roj.config.auto.SerializerFactory;
import roj.io.IOUtil;
import roj.ui.Terminal;
import roj.ui.terminal.Argument;
import roj.ui.terminal.CommandContext;
import roj.ui.terminal.SimpleCliParser;

import java.io.File;
import java.util.List;

import static roj.text.diff.DiffResult.bar;
import static roj.ui.terminal.CommandNode.argument;
import static roj.ui.terminal.SimpleCliParser.nullImpl;

/**
 * @author Roj234
 * @since 2023/8/2 0002 6:39
 */
public class DiffUser {
	public static void main(String[] args) throws Exception {
		CommandContext ctx = new SimpleCliParser()
			.add(argument("basePath", Argument.folder())
					.then(argument("diffYml", Argument.file())
						.executes(nullImpl())))
			.parse(args, true);

		if (ctx == null) {
			System.out.println("DiffUser <basePath> <diffYml> [isText=true]");
			return;
		}

		File basePath = ctx.argument("basePath", File.class);

		Serializer<List<DiffResult>> adapter = SerializerFactory.SAFE.listOf(DiffResult.class);
		File file = ctx.argument("diffYml", File.class);
		List<DiffResult> diffs = ConfigMaster.YAML.readObject(adapter, file);
		for (int i = diffs.size() - 1; i >= 0; i--) {
			DiffResult d = diffs.get(i);

			if (d.left.compareTo(d.right) > 0) {
				String tmp = d.left;
				d.left = d.right;
				d.right = tmp;
			}

			d.leftFile = new File(basePath, d.left);
			d.rightFile = new File(basePath, d.right);

			if(!d.leftFile.isFile() || !d.rightFile.isFile()) {
				System.out.println("removed: "+d);
				diffs.remove(i);
				continue;
			}

			bar.addMax(1);
			POOL.submit(() -> d.postProcess(true));
		}

		POOL.awaitFinish();

		bar.end("ok");
		bar.reset();
		diffs.sort((o1, o2) -> Integer.compare(o1.diff, o2.diff));
		for (int i = diffs.size() - 1; i > 0; i--) {
			DiffResult d = diffs.get(i);
			if (d.equals(diffs.get(i - 1))) {
				System.out.println("same:"+d);
				diffs.remove(i);
			}
		}
		System.out.println("count:" + diffs.size());
		ConfigMaster.YAML.writeObject(adapter, diffs, new File(file.getParentFile(), IOUtil.fileName(file.getName())+".new.yml"));

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

			int exit;
			new ProcessBuilder().command("D:\\_USoft\\Everything\\Everything.exe", "-s", "<" + Tokenizer.addSlashes(IOUtil.fileName(d.leftFile.getName())) + ">|<" + Tokenizer.addSlashes(IOUtil.fileName(d.rightFile.getName())) + '>').start();
			exit = new ProcessBuilder().command(args2).start().waitFor();

			if (exit == 0) {
				if (d.leftFile.getAbsolutePath().contains("aaaa")) {
					d.leftFile.delete();
					System.out.println("auto "+"("+d.leftFile+")");
				} else if (d.rightFile.getAbsolutePath().contains("aaaa")) {
					d.rightFile.delete();
					System.out.println("auto "+"("+d.rightFile+")");
				} else {
					System.out.println("left:" + d.left);
					System.out.println("right:" + d.right);
					System.out.println("删除左(l)右(r)取消(c)");
					char c = Terminal.readChar(MyBitSet.from("lrc"));
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
			}

			System.out.println();
		}
	}

	private static final TaskPool POOL = TaskPool.MaxSize(9999, "TDD worker");
}