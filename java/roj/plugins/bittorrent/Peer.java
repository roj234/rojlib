package roj.plugins.bittorrent;

/**
 * @author Roj234
 * @since 2025/05/21 04:34
 */
class Peer {
	static final int REMOTE_INTEREST = 1, LOCAL_CHOKE = 2, LOCAL_INTEREST = 4, REMOTE_CHOKE = 8;
	int state;

	void tick(Session session) {

	}
}
