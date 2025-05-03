package roj.plugins.minecraft.server.network;

import org.intellij.lang.annotations.Language;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/3/19 21:04
 */
public class MinecraftException extends IOException {
	public final String sendToPlayer;

	public MinecraftException(@Language("json") String message) {
		super(message);
		this.sendToPlayer = message;
	}

	public MinecraftException(@Language("json") String message, Throwable cause) {
		super(message, cause);
		this.sendToPlayer = message;
	}
}