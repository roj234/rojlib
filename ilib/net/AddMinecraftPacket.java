package ilib.net;

import net.minecraft.network.EnumConnectionState;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Roj234
 * @since 2022/9/7 0007 11:14
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface AddMinecraftPacket {
	EnumConnectionState state() default EnumConnectionState.PLAY;

	int value() default ANY;

	int ANY = 0, TO_CLIENT = 1, TO_SERVER = 2;
}
