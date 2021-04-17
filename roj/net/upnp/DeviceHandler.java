package roj.net.upnp;

/**
 * @author solo6975
 * @since 2022/1/16 0:32
 */
public interface DeviceHandler {
	void threadStart(Thread t);

	void threadStop();

	void onDeviceJoin(UPnPDevice device);

	void onError(String error);

	void onException(Throwable e);
}
