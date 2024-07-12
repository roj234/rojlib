package roj.net.http.h2;

import org.jetbrains.annotations.NotNull;

/**
 * @author Roj234
 * @since 2024/7/13 5:53
 */
public interface H2Manager {
	default void initSetting(H2Setting setting) {setting.max_streams = 256;}
	default void validateRemoteSetting(H2Setting setting) throws H2Exception {setting.sanityCheck();}
	@NotNull
	H2Stream createStream(int id);
}