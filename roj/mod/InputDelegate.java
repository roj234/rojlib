package roj.mod;

import roj.collect.SimpleList;
import roj.config.JSONParser;
import roj.config.data.CMapping;
import roj.io.IOUtil;
import roj.math.Version;
import roj.mod.mapping.VersionRange;
import roj.ui.CmdUtil;
import roj.ui.UIUtil;

import java.io.File;
import java.io.IOException;

import static roj.mod.Shared.BASE;

/**
 * Input operation delegation between CLI and Automatic
 *
 * @author Roj234
 * @since 2023/1/15 10:22
 */
class InputDelegate {
	Version version;
	String id;

	void init(Version v, CMapping json, String id) {
		version = v;
		this.id = id;
	}

	CMapping getSelectedMappingFormat() throws IOException {
		SimpleList<CMapping> list = new SimpleList<>();

		File[] files = new File(BASE, "util/mapping").listFiles();
		for (File file : files) {
			if (file.getName().equals("@common.json")) continue;
			try {
				CMapping map = JSONParser.parses(IOUtil.readUTF(file)).asMap();
				VersionRange range = VersionRange.parse(map.getString("version"));
				if (range.suitable(version) && map.getString("type").equals(id)) list.add(map);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		CmdUtil.info("有多个 Mapping , 请选择(输入编号)");

		for (int i = 0; i < list.size(); i++) {
			String name = list.get(i).getString("title");
			CmdUtil.fg(CmdUtil.Color.WHITE, (i & 1) == 1);
			System.out.println(i + ". " + name);
			CmdUtil.reset();
		}

		return list.get(UIUtil.getNumberInRange(0, list.size()));
	}

	String getMinecraftVersionForMCP() throws IOException {
		return UIUtil.userInput("不知道MCP的版本号,使用" + version + "需修改请输入并按回车: ");
	}

	String getMCPVersion() throws IOException {
		return UIUtil.userInput("MCP");
	}
}
