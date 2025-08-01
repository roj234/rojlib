/*
 * MemoryLimitException
 *
 * Author: Lasse Collin <lasse.collin@tukaani.org>
 *
 * This file has been put into the public domain.
 * You can do whatever you want with this file.
 */

package roj.archive.xz;

import roj.text.TextUtil;

/**
 * Thrown when the memory usage limit given to the XZ decompressor
 * would be exceeded.
 * <p>
 * The amount of memory required and the memory usage limit are
 * included in the error detail message in human-readable format.
 */
public class MemoryLimitException extends java.io.IOException {
	private static final long serialVersionUID = 3L;

	private final int memoryNeeded;
	private final int memoryLimit;

	/**
	 * Creates a new MemoryLimitException.
	 * <p>
	 * The amount of memory needed and the memory usage limit are
	 * included in the error detail message.
	 *
	 * @param memoryNeeded amount of memory needed as kibibytes (KiB)
	 * @param memoryLimit specified memory usage limit as kibibytes (KiB)
	 */
	public MemoryLimitException(int memoryNeeded, int memoryLimit) {
		super(TextUtil.scaledNumber1024(memoryNeeded*1024L)+" of memory would be needed; limit was "+TextUtil.scaledNumber1024(memoryLimit*1024L));

		this.memoryNeeded = memoryNeeded;
		this.memoryLimit = memoryLimit;
	}

	/**
	 * Gets how much memory is required to decompress the data.
	 *
	 * @return amount of memory needed as kibibytes (KiB)
	 */
	public int getMemoryNeeded() { return memoryNeeded; }

	/**
	 * Gets what the memory usage limit was at the time the exception
	 * was created.
	 *
	 * @return memory usage limit as kibibytes (KiB)
	 */
	public int getMemoryLimit() { return memoryLimit; }
}