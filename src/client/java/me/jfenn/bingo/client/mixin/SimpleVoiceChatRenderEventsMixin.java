package me.jfenn.bingo.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;

@Pseudo
@Mixin(targets = "de.maxhenkel.voicechat.voice.client.RenderEvents", remap = false)
public abstract class SimpleVoiceChatRenderEventsMixin {

    private static Method onboardingIsOnboarding;
    private static Method clientManagerGetClient;
    private static Method clientVoicechatGetConnection;
    private static Method clientVoicechatConnectionIsInitialized;

    // Simple Voice Chat reads the client/connection repeatedly here; keep one connection snapshot
    // so a voice reset during lobby interaction-entity name-tag events cannot race into an NPE.
    @Inject(method = "shouldShowIcons", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void exbingo$shouldShowIconsSafely(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(exbingo$computeShouldShowIcons());
    }

    private static boolean exbingo$computeShouldShowIcons() {
        if (exbingo$isOnboarding()) {
            return false;
        }

        Object client = exbingo$getClient();
        Object connection = exbingo$getConnection(client);
        if (connection != null && exbingo$isInitialized(connection)) {
            return true;
        }

        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        return server == null || server.isPublished();
    }

    private static boolean exbingo$isOnboarding() {
        try {
            if (onboardingIsOnboarding == null) {
                onboardingIsOnboarding = Class
                        .forName("de.maxhenkel.voicechat.gui.onboarding.OnboardingManager")
                        .getMethod("isOnboarding");
            }
            return Boolean.TRUE.equals(onboardingIsOnboarding.invoke(null));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private static Object exbingo$getClient() {
        try {
            if (clientManagerGetClient == null) {
                clientManagerGetClient = Class
                        .forName("de.maxhenkel.voicechat.voice.client.ClientManager")
                        .getMethod("getClient");
            }
            return clientManagerGetClient.invoke(null);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    private static Object exbingo$getConnection(Object client) {
        if (client == null) {
            return null;
        }

        try {
            if (clientVoicechatGetConnection == null) {
                clientVoicechatGetConnection = client.getClass().getMethod("getConnection");
            }
            return clientVoicechatGetConnection.invoke(client);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    private static boolean exbingo$isInitialized(Object connection) {
        try {
            if (clientVoicechatConnectionIsInitialized == null) {
                clientVoicechatConnectionIsInitialized = connection.getClass().getMethod("isInitialized");
            }
            return Boolean.TRUE.equals(clientVoicechatConnectionIsInitialized.invoke(connection));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }
}
