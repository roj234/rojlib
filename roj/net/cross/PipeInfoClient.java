package roj.net.cross;

/**
 * @author Roj233
 * @since 2021/12/25 17:24
 */
final class PipeInfoClient {
	int clientId, pipeId;
	byte portId;

	public PipeInfoClient(int c, int p, int po) {
		clientId = c;
		pipeId = p;
		portId = (byte) po;
	}

	@Override
	public String toString() { return clientId+":"+Integer.toHexString(pipeId)+"#"+portId; }
}
