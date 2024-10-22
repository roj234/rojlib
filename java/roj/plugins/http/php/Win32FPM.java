package roj.plugins.http.php;

import roj.collect.RingBuffer;
import roj.collect.SimpleList;
import roj.concurrent.task.ITask;
import roj.concurrent.timing.Scheduler;
import roj.io.FastFailException;
import roj.net.http.IllegalRequestException;
import roj.net.http.server.Request;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Roj234
 * @since 2024/7/2 1:55
 */
public class Win32FPM extends fcgiManager implements ITask {
	final List<String> command;
	final int minProcess, maxProcess, timeout;
	final ConcurrentHashMap<Process, fcgiConnection> processes = new ConcurrentHashMap<>();
	int portBase = 40000;
	private int launchedSize;

	File docRoot;

	public Win32FPM(int minProcess, int maxProcess, int timeout, List<String> command) {
		this.command = command;
		this.minProcess = minProcess;
		this.maxProcess = maxProcess;
		this.timeout = timeout;

		if (timeout != 0) Scheduler.getDefaultScheduler().loop(this::killStale, timeout);
	}

	public boolean killStale() {
		if (processes.size() > minProcess) {
			long deadline = System.currentTimeMillis() - timeout;
			var itr = processes.entrySet().iterator();
			if (itr.hasNext()) {
				var next = itr.next();
				if (next.getValue().idleTime < deadline) {
					next.getKey().destroy();
					itr.remove();
					return true;
				}
			}
		}
		return false;
	}

	public void killAll() {
		for (Process process : processes.keySet()) {
			process.destroy();
		}
	}

	private boolean stop;
	public void stop() {
		lock.lock();
		try {
			stop = true;
			stateChanged.signal();
		} finally {
			lock.unlock();
		}
	}

	protected void fcgi_set_param(Request req, Map<String, String> param) throws IllegalRequestException {
		String scriptName = req.path();

		File scriptFile = new File(docRoot, scriptName);
		if (!scriptFile.isFile() || !scriptFile.getName().endsWith(".php")) throw new IllegalRequestException(404);

		param.put("DOCUMENT_ROOT", docRoot.getAbsolutePath());
		param.put("SCRIPT_NAME", scriptName.replace('/', File.separatorChar));
		param.put("PATH_TRANSLATED", scriptFile.getAbsolutePath());
		param.put("SCRIPT_FILENAME", scriptFile.getAbsolutePath());
	}

	private final ReentrantLock lock = new ReentrantLock();
	private final Condition stateChanged = lock.newCondition();

	final RingBuffer<fcgiResponse> pendingTask = new RingBuffer<>(1024, false);
	private final RingBuffer<Map<String, String>> pendingParam = new RingBuffer<>(1024, false);

	protected void fcgi_attach(fcgiResponse response, Map<String, String> param) {
		lock.lock();
		try {
			if (!pendingTask.offerLast(response)) {
				response.fail(new FastFailException("排队的请求太多"));
				return;
			}
			pendingParam.offerLast(param);
			stateChanged.signal();
		} finally {
			lock.unlock();
		}
	}

	public void execute() throws Exception {
		while (true) {
			fcgiResponse response;
			Map<String, String> param;

			for(;;) {
				lock.lock();
				try {
					response = pendingTask.pollFirst();
					if (response != null) {
						param = pendingParam.removeFirst();

						var error = response.isInvalid();
						if (error == null) break;
						fcgiManager.LOGGER.warn("请求({})不再有效:{}",response,error);
					}

					if (stop) return;
					while (pendingTask.isEmpty()) stateChanged.await();
					if (stop) return;
				} finally {
					lock.unlock();
				}
			}

			ok:
			for(;;) try {
				for (var itr = processes.entrySet().iterator(); itr.hasNext(); ) {
					var entry = itr.next();
					if (!entry.getKey().isAlive()) {
						itr.remove();
						continue;
					}

					var conn = entry.getValue();
					int code = conn.attach(response, param);
					if (code > 0) break ok;
					if (code < 0) {
						entry.getKey().destroy();
						itr.remove();
					}
				}

				if (processes.size() < maxProcess) {
					SimpleList<String> myArgs = new SimpleList<>(command.size()+1);
					myArgs.addAll(command);

					int port = portBase + launchedSize++ % 1000;
					myArgs.add(String.valueOf(port));

					var key = new ProcessBuilder(myArgs).directory(new File(myArgs.get(0)).getParentFile()).start();
					var val = new fcgiConnection(this, port);

					if (val.attach(response, param) > 0) {
						processes.put(key, val);
						break;
					} else {
						key.destroy();
					}
				}

				lock.lock();
				try {
					stateChanged.await();
				} finally {
					lock.unlock();
				}
			} catch (Exception e) {
				response.fail(e);
			}
		}
	}

	private void hasSpace() {
		lock.lock();
		stateChanged.signalAll();
		lock.unlock();
	}

	@Override
	protected void connectionClosed(fcgiConnection conn) {hasSpace();}
	@Override
	protected void requestFinished(fcgiConnection conn) {hasSpace();}
}