/*
 * FinishableOutputStream
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package roj.io;

import java.io.IOException;

/**
 * Output stream that supports finishing without closing
 * the underlying stream.
 */
public interface Finishable {
	void finish() throws IOException;
}
