package roj.staging;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * @author Roj234
 * @since 2025/08/24 05:07
 */
public class Utils {
	public static void main(String[] args) throws Exception {
	}

	public static UUID createOfflinePlayerUUID(String playername) {
		return UUID.nameUUIDFromBytes(("OfflinePlayer:" + playername).getBytes(StandardCharsets.UTF_8));
	}
}
