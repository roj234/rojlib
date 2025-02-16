package roj.plugins.aiapi;

import roj.config.ConfigMaster;
import roj.config.auto.SerializerFactory;
import roj.config.table.TableWriter;
import roj.text.DateParser;
import roj.text.TextUtil;
import roj.ui.EasyProgressBar;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Map;

/**
 * @author Roj234
 * @since 2024/2/26 0026 23:55
 */
public class TranslateTask {
	public static void main(String[] args) throws Exception {
		var task = ChatTask.fromFile(new File("plugins/AIApi/templates/translate_en.json"));
		var ser = SerializerFactory.UNSAFE.mapOf(String.class);

		var recovery = new RandomAccessFile("progress.dat", "rw");
		recovery.setLength(4);

		Map<String, String> map = ConfigMaster.JSON.readObject(ser, new File("input.json"));

		var out = TableWriter.csvWriterAppend("output.csv");
		out.writeRow(Arrays.asList("text","translated","success", DateParser.toLocalTimeString(System.currentTimeMillis())));

		int finishedCount = recovery.readInt();
		int i = finishedCount;

		var bar = new EasyProgressBar("翻译", "句");
		bar.setTotal(map.size());
		bar.increment(finishedCount);

		long lastSave = System.currentTimeMillis();
		try {
			for (var entry : map.entrySet()) {
				if (finishedCount > 0) {finishedCount--;continue;}

				var key = entry.getValue();
				var text = task.eval(key).trim();

				int editDistance = TextUtil.editDistance(key, text);
				int error = 0;
				if (text.length() > key.length()+30) {
					System.out.println("对 "+key+" 的翻译未达标，正在尝试重新翻译...");

					task.rewindContext();
					text = task.eval(key).trim();

					if (text.length() > key.length()+30) {
						System.out.println("重试翻译仍未达标，记录到错误列表。");
						error = 1;
					}
				} else if ((double) editDistance / key.length() < 0.2) {
					System.out.println("对 "+key+" 的翻译相似度过高，记录到错误列表。");
					error = 2;
				}

				System.out.println("编辑距离="+editDistance);
				out.writeRow(Arrays.asList(entry.getKey(),text,error));
				bar.increment(1);

				long time = System.currentTimeMillis();
				if (time - lastSave > 60000) {
					lastSave = time;

					recovery.seek(0);
					recovery.writeInt(i);
				}
			}
		} finally {
			out.close();
		}

		bar.end("任务已完成");
		recovery.close();
		new File("checkpoint.dat").delete();
	}
}