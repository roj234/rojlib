package roj.plugins.bittorrent;

import roj.concurrent.OperationDone;
import roj.concurrent.Promise;
import roj.config.ConfigMaster;
import roj.config.ParseException;
import roj.config.data.CEntry;
import roj.config.data.CMap;
import roj.http.HttpHead;
import roj.http.HttpRequest;
import roj.io.FastFailException;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author Roj234
 * @since 2025/05/20 20:00
 */
public abstract class Tracker {
	abstract Promise<Void> update(Session session);
	abstract int errorCount();

	public static Tracker httpTracker(String url) {return new HTTP(url);}
	public static Tracker udpTracker(String url) {return new UDP(url);}
	public static Tracker groupTracker(List<Tracker> trackers) {return new Group(trackers);}

	static class HTTP extends Tracker {
		final String url;
		long interval;
		int leechers, seeders;
		int errorCount;

		HTTP(String url) {this.url = url;}

		@Override
		Promise<Void> update(Session session) {
			long time = System.currentTimeMillis() / 1000;
			long notifyAfter = session.isDirty() ? 0 : interval;

			if (time < notifyAfter) return errorCount > 0 ? Promise.reject(OperationDone.INSTANCE) : Promise.resolve(null);

			return Promise.<CMap>async(callback -> {
				try {
					HttpRequest.builder().url(url).query(session.getHttpParameter()).bodyLimit(1048576).execute(120000).await(httpClient -> {
						if (!httpClient.isDone()) return;
						try {
							HttpHead head = httpClient.head();
							if (head.getCode() != 200) {
								System.out.println(head);
							}
							callback.resolve(ConfigMaster.BENCODE.parse(httpClient.bytes()).asMap());
						} catch (IOException | ParseException e) {
							// 如果不是success状态，那么#bytes会异常
							callback.reject(e);
						}
					});
				} catch (Exception e) {
					callback.reject(e);
				}
			}).thenAccept(data -> {
				System.out.println("response="+data);

				if (data.containsKey("failure reason"))
					throw new FastFailException("Tracker response: "+data.getString("failure reason"));

				errorCount = 0;
				interval = System.currentTimeMillis() / 1000 + data.getInt("interval");
				leechers = data.getInt("incomplete");
				seeders = data.getInt("complete");
				for (CEntry item : data.getList("peers").raw()) {
					CMap map = item.asMap();
					session.addPeer(map.getString("peer id"), new InetSocketAddress(map.getString("IP"), map.getInt("Port")));
				}
			}).rejected(exc -> {
				exc.printStackTrace();

				interval = System.currentTimeMillis() / 1000 + (10L << errorCount);
				errorCount++;
				return null;
			});
		}

		@Override
		int errorCount() {return errorCount;}
	}
	static final class UDP extends HTTP {
		UDP(String url) {super(url);}

		@Override
		Promise<Void> update(Session session) {
			long time = System.currentTimeMillis() / 1000;
			long notifyAfter = session.isDirty() ? 0 : interval;

			if (time < notifyAfter) return errorCount > 0 ? Promise.reject(OperationDone.INSTANCE) : Promise.resolve(null);

			return Promise.async(callback -> {
				try {
					URI uri = URI.create(url);
					InetSocketAddress address = new InetSocketAddress(InetAddress.getByName(uri.getHost()), uri.getPort());

					session.registerUdpHandler(address, new Session.UDPHandler() {
						int transactionId;
						long connectionId;
						int state;
						boolean isIPV6;
						ByteList buffer = new ByteList();

						@Override
						public DynByteBuf next(DynByteBuf buf) throws IOException {
							switch (state) {
								// get connectionId
								case 0 -> {
									state++;
									transactionId = ThreadLocalRandom.current().nextInt();
									return buffer.putLong(0x41727101980L).putInt(0).putInt(transactionId);
								}
								// query data
								case 1 -> {
									if (buf.readableBytes() != 16) break;
									if (buf.readInt() != 0) break;
									if (buf.readInt() != transactionId) break;

									state++;
									transactionId = ThreadLocalRandom.current().nextInt();
									connectionId = buf.readLong();

									DynByteBuf out = buffer; out.clear();
									out.putLong(connectionId).putInt(1).putInt(transactionId);
									session.getUdpParameter(out);
									return out;
								}
								case 2 -> {
									if (buf.readableBytes() < 20) break;
									if (buf.readInt() != 1) break;
									if (buf.readInt() != transactionId) break;

									state++;
									interval = System.currentTimeMillis() / 1000 + buf.readInt();
									leechers = buf.readInt();
									seeders = buf.readInt();

									while (buf.isReadable()) {
										InetAddress address = InetAddress.getByAddress(buf.readBytes(isIPV6 ? 16 : 4));
										session.addPeer(null, new InetSocketAddress(address, buf.readUnsignedShort()));
									}

									callback.resolve("ok");
									return null;
								}
							}

							callback.reject(new FastFailException("state error"));
							return null;
						}
					});
				} catch (Exception e) {
					callback.reject(e);
				}
			}).thenAccept(data -> {
				System.out.println("response="+data);

			}).rejected(exc -> {
				exc.printStackTrace();

				interval = System.currentTimeMillis() / 1000 + (10L << errorCount);
				errorCount++;
				return null;
			});
		}
	}

	static final class Group extends Tracker {
		final List<Tracker> trackers;
		int previousIndex;
		int errorCount;

		Group(List<Tracker> trackers) {this.trackers = trackers;}

		@Override
		Promise<Void> update(Session session) {
			Promise<Void> sync = Promise.sync();
			Promise.Callback callback = (Promise.Callback) sync;

			var obj = new Object() {
				int i = 0;
				void next() {
					if (i == trackers.size()) {
						errorCount++;
						callback.reject(new FastFailException("Nothing completed"));
						return;
					}

					int index = (i + previousIndex) % trackers.size();
					trackers.get(index)
							.update(session)
							.then(
									(success, _callback) -> {
										previousIndex = index;
										callback.resolve(null);
									},
									failure -> { next(); return null; }
							);

					i++;
				}
			};

			obj.next();
			return sync;
		}

		@Override int errorCount() {return errorCount;}
	}
}
