package roj.misc;

import roj.config.NBTParser;
import roj.config.data.CMapping;
import roj.io.MyRegionFile;
import roj.ui.UIUtil;

import java.io.DataInput;
import java.io.File;

/**
 * @author Roj234
 * @since 2022/10/16 0016 12:45
 */
public class ChunkDump {
	public static void main(String[] args) throws Exception {
		MyRegionFile regionFile = new MyRegionFile(new File(args[0]));

		System.out.print("Avail.Regions: ");
		for (int i = 0; i < 1024; i++) {
			if (regionFile.hasData(i)) {
				System.out.print(i+", ");
			}
		}

		System.out.println();
		while (true) {
			CMapping map = NBTParser.parse((DataInput) regionFile.getDataInput(UIUtil.getNumberInRange(0, 1024)));
			System.out.println(map);
		}
	}
}
