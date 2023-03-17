/*
 * UnsupportedOptionsException
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package roj.archive.qz.xz;

/**
 * Thrown when compression options not supported by this implementation
 * are detected. Some other implementation might support those options.
 */
public class UnsupportedOptionsException extends java.io.IOException {
	private static final long serialVersionUID = 3L;
	public UnsupportedOptionsException() {}
	public UnsupportedOptionsException(String s) {
		super(s);
	}
}
