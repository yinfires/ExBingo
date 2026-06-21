package me.jfenn.bingo.mixin;

import me.jfenn.bingo.common.WorldDeleter;
import me.jfenn.bingo.mixinhandler.MinecraftServerMixinHandler;
import me.jfenn.bingo.platform.scope.BingoKoin;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.network.ServerConnectionListener;
import net.minecraft.util.profiling.metrics.profiling.MetricsRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerUnsafeSkipCloseMixin {

    @Unique
    private final Logger log = LoggerFactory.getLogger(MinecraftServerUnsafeSkipCloseMixin.class);

    @Shadow
    private boolean isSaving;

    @Shadow @Final
    private ServerConnectionListener connection;

    @Shadow
    private PlayerList playerList;

    @Shadow private MetricsRecorder metricsRecorder;

    @Shadow public abstract void stopRecordingMetrics();

    @Unique
    private boolean shouldKeepWorldData() {
        MinecraftServer server = (MinecraftServer) (Object) this;
        if (BingoKoin.INSTANCE.getScope(server) == null) return true;

        // if this is running client-side, just shut down normally
        if (!server.isDedicatedServer()) return true;

        // if isLobbyMode=false, never delete any world data
        if (!MinecraftServerMixinHandler.INSTANCE.shouldDeleteWorld(server)) {
            return true;
        }

        // if a game is running, the server should restart normally to save all world data
        return MinecraftServerMixinHandler.INSTANCE.isGamePlaying(server);
    }

    @Inject(at = @At(value = "HEAD"), method = "stopServer", cancellable = true)
    public void shutdownUnsafe(CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        if (shouldKeepWorldData()) return;

        // Only proceed if unsafeSkipWorldClose=true
        if (!MinecraftServerMixinHandler.INSTANCE.isUnsafeSkipWorldClose(server)) return;

        // many of the shutdown tasks can be skipped, since we don't care if all the world data is saved
        log.info("Stopping server");

        if (this.metricsRecorder != null && this.metricsRecorder.isRecording()) {
            this.stopRecordingMetrics();
        }

        if (this.connection != null) {
            this.connection.stop();
        }

        this.isSaving = true;

        if (this.playerList != null) {
            log.info("Disconnecting players");
            try {
                this.playerList.removeAll();
            } catch (Throwable e) {
                log.error("Error on removeAll", e);
            }
        }

        log.info("unsafeSkipWorldClose is true; skipping file closing");
        log.info("This will likely cause a crash...");

        this.isSaving = false;

        // finally, delete the world files before restarting
        WorldDeleter.INSTANCE.invoke(server);

        // immediately halt the JVM (like System.exit(0) but *worse!*)
        Runtime.getRuntime().halt(0);
        ci.cancel();
    }

    @Inject(at = @At(value = "TAIL"), method = "stopServer", cancellable = true)
    public void shutdownDeleteWorld(CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        if (shouldKeepWorldData()) return;

        // finally, delete the world files before restarting
        WorldDeleter.INSTANCE.invoke(server);
        ci.cancel();
    }

}
