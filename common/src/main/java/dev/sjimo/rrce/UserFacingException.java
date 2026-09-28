package dev.sjimo.rrce;

import net.minecraft.network.chat.Component;

/** A validation failure whose text is translated on the receiving client. */
public final class UserFacingException extends IllegalArgumentException {
    private final String translationKey;
    private final Object[] arguments;

    public UserFacingException(String translationKey, Object... arguments) {
        super(translationKey);
        this.translationKey = translationKey;
        this.arguments = arguments.clone();
    }

    public Component component() {
        return Component.translatable(translationKey, arguments);
    }

    public static Component display(RuntimeException error) {
        return error instanceof UserFacingException facing
            ? facing.component() : Component.translatable("error.rrce.unexpected");
    }
}
