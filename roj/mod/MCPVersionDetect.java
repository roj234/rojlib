package roj.mod;

import roj.ui.CLIUtil;

import java.io.IOException;
import java.util.Map;

/**
 * @author Roj234
 * @since 2023/1/7 0007 7:33
 */
public class MCPVersionDetect {
	private static String detectVersion(String version) {
		switch (version) {
			case "1.8.1": case "1.8.2": case "1.8.3":
			case "1.8.4": case "1.8.6": case "1.8.7": return "1.8";
			case "1.9.1": case "1.9.2": case "1.9.3": return "1.9";
			case "1.10": case "1.10.1": return "1.10.2";
			case "1.11.1": case "1.11.2": return "1.11";
			case "1.12.1": case "1.12.2": return "1.12";
		}
		return version;
	}

	private static String getStableMCPVersion(String version) {
		switch (version) {
			case "1.7.10": return "12";
			case "1.8": return "18";
			case "1.8.8": return "20";
			case "1.8.9": return "22";
			case "1.9": return "24";
			case "1.9.4": return "26";
			case "1.10.2": return "29";
			case "1.11": return "32";
			case "1.12": return "39";
			case "1.13": return "43";
			case "1.13.1": return "45";
			case "1.13.2": return "47";
			case "1.14": return "49";
			case "1.14.1": return "51";
			case "1.14.2": return "53";
			case "1.14.3": return "56";
			case "1.14.4": return "58";
			case "1.15": return "60";
		}
		return null;
	}

	public static void doDetect(Map<String, Object> cfg) throws IOException {
		CLIUtil.warning("自动匹配数据库更新在2021年2月(由于用户太少),出错请反馈,或手动选择");

		String version = cfg.get("version").toString();
		String mcpVersion = detectVersion(version);
		if (mcpVersion == version) {
			String newMcp = CLIUtil.userInput("不知道MCP的版本号,使用"+version+"需修改请输入并按回车: ").trim();
			if (newMcp.length() > 0) mcpVersion = newMcp;
		}
		String stable = getStableMCPVersion(mcpVersion);
		if (stable == null || Shared.DEBUG) {
			CLIUtil.info("stable-123 写为 !123 (稳定版)  snapshot-20201221 缩写为 20201221 (快照版) ");

			String subVersion = CLIUtil.userInput("MCP").trim();

			if (subVersion.startsWith("!")) {
				cfg.put("var.mcp-kind", "stable");
				cfg.put("var.mcp-ver", subVersion.substring(1)+'-'+mcpVersion);
			} else {
				cfg.put("var.mcp-kind", "snapshot");
				cfg.put("var.mcp-ver", subVersion+'-'+mcpVersion);
			}
		} else {
			cfg.put("var.mcp-kind", "stable");
			cfg.put("var.mcp-ver", stable+'-'+mcpVersion);
		}
	}
}