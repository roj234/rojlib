package roj.config;

/**
 * @author Roj234
 * @since 2024/11/8 20:09
 */
public interface Flags {
	/**
	 * 解析注释<b>初始化Flag</b> <br>
	 * 适用于: JSON YAML CCJSON CCYAML TOML
	 */
	int COMMENT = 1;

	/**
	 * 弱化部分解析限制 <br>
	 * 适用于: YAML CCYAML TOML XML
	 */
	int LENIENT = 1;
	/**
	 * 检测并拒绝CMap中重复的key <br>
	 * 适用于: JSON YAML INI
	 */
	int NO_DUPLICATE_KEY = 2;
	/**
	 * 使用{@link roj.collect.LinkedMyHashMap}充当CMap中的map来保留配置文件key的顺序 <br>
	 * 适用于: JSON YAML TOML INI
	 */
	int ORDERED_MAP = 4;
}
