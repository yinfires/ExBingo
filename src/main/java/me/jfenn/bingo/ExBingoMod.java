package me.jfenn.bingo;

import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import me.jfenn.bingo.impl.networking.NeoForgePacketRegistry;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(ExBingoMod.MOD_ID)
public final class ExBingoMod {

    public static final String MOD_ID = "exbingo";
    private static final Logger LOGGER = LoggerFactory.getLogger("ExBingo");
    private static IEventBus modEventBus;

    public ExBingoMod(IEventBus modEventBus) {
        ExBingoMod.modEventBus = modEventBus;
        modEventBus.addListener(RegisterPayloadHandlersEvent.class, NeoForgePacketRegistry.INSTANCE::registerPayloads);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            registerClientModBusListeners(modEventBus);
        }

        LOGGER.info("Starting ExBingo NeoForge entrypoint...");
        invokeInitializer(FMLEnvironment.dist == Dist.CLIENT
                ? "me.jfenn.bingo.client.Main"
                : "me.jfenn.bingo.server.Main",
                FMLEnvironment.dist == Dist.CLIENT ? "initClient" : "initServer");

        if (!ModList.get().isLoaded("jei")) {
            LOGGER.debug("JEI is not installed; ExBingo will use its fallback recipe integration.");
        }
        if (!ModList.get().isLoaded("voicechat")) {
            LOGGER.debug("Simple Voice Chat is not installed; ExBingo will use its dummy voice API.");
        }
    }

    private static void invokeInitializer(String className, String methodName) {
        try {
            Class<?> type = Class.forName(className);
            Object instance = type.getField("INSTANCE").get(null);
            type.getMethod(methodName).invoke(instance);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to start ExBingo initializer " + className + "#" + methodName, e);
        }
    }

    public static IEventBus getModEventBus() {
        return modEventBus;
    }

    private static void registerClientModBusListeners(IEventBus modEventBus) {
        try {
            Class<?> keyBindings = Class.forName("me.jfenn.bingo.client.impl.KeyBindingManager");
            modEventBus.addListener(
                    net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent.class,
                    event -> {
                        try {
                            keyBindings.getMethod("registerMappings", event.getClass()).invoke(null, event);
                        } catch (ReflectiveOperationException e) {
                            throw new IllegalStateException("Unable to register ExBingo key mappings", e);
                        }
                    }
            );

            Class<?> clientEvents = Class.forName("me.jfenn.bingo.client.impl.event.ClientEventsImpl");
            modEventBus.addListener(
                    net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent.class,
                    event -> {
                        try {
                            clientEvents.getMethod("registerReloadListeners", event.getClass()).invoke(null, event);
                        } catch (ReflectiveOperationException e) {
                            throw new IllegalStateException("Unable to register ExBingo client reload listeners", e);
                        }
                    }
            );
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Unable to prepare ExBingo client mod bus listeners", e);
        }
    }
}
