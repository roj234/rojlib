package roj.ci;

import roj.collect.ToIntMap;
import roj.collect.ToLongMap;
import roj.config.ConfigMaster;
import roj.config.mapper.Optional;
import roj.io.IOUtil;
import roj.text.ParseException;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * @author Roj234
 * @since 2025/09/17 08:44
 */
@Optional
public class Statistic {
	public long since = System.currentTimeMillis();
	public ToIntMap<String> buildCounts = new ToIntMap<>();
	public ToIntMap<String> buildFailures = new ToIntMap<>();
	public ToLongMap<String> buildTimes = new ToLongMap<>();

	private static Statistic instance = new Statistic();
	private static boolean isDirty;

	public static void afterProjectBuild(String projectName, long timeConsumed, Set<String> args, boolean success) {
		String trigger = projectName+":"+(args.contains("auto") ? "auto" : "manual");
		if (success) {
			instance.buildTimes.increment(projectName, timeConsumed);
		} else {
			instance.buildFailures.increment(trigger, 1);
		}
		instance.buildCounts.increment(trigger, 1);

		isDirty = true;
	}

	static {init();}
	private static void init() {
		var file = new File(MCMake.CONF_PATH, "statistics.json");
		if (file.isFile()) {
			try {
				instance = ConfigMaster.JSON.readObject(MCMake.CONFIG.serializer(Statistic.class), file);
			} catch (IOException | ParseException e) {
				MCMake.LOGGER.error("Exception while reading statistics.json", e);
				return;
			}
		}

		Runtime.getRuntime().addShutdownHook(new Thread(Statistic::save));
		MCMake.TIMER.loop(Statistic::save, 60000 * 30); // every 30 min
	}

	private static void save() {
		if (isDirty) {
			try {
				IOUtil.writeFileEvenMoreSafe(MCMake.CONF_PATH, "statistics.json",
						value -> ConfigMaster.JSON.writeObject(MCMake.CONFIG.serializer(Statistic.class), instance, value));
				isDirty = false;
			} catch (IOException e) {
				MCMake.LOGGER.warn("Exception saving statistics.json", e);
			}
		}
	}
}
