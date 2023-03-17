/*
 * FinishableOutputStream
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package roj.archive.qz.xz;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Output stream that supports finishing without closing
 * the underlying stream.
 */
public abstract class FinishableOutputStream extends OutputStream {
	/**
	 * Finish the stream without closing the underlying stream.
	 * No more data may be written to the stream after finishing.
	 */
	public void finish() throws IOException {}
}
