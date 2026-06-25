package me.jfenn.bingo.client.mixin;

import net.neoforged.neoforge.network.DualStackUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fixes "Open to LAN" rooms being undiscoverable in other players' multiplayer lists.
 *
 * <p>NeoForge replaced Vanilla's hard-coded LAN-discovery multicast group ({@code 224.0.2.60})
 * with {@link DualStackUtils#getMulticastGroup()}, which returns the IPv6 group
 * {@code FF75:230::60} whenever the host's local address resolves as IPv6. On dual-stack
 * machines (common on Windows 11) the wildcard local address is frequently detected as IPv6,
 * so the host broadcasts {@link net.minecraft.client.server.LanServerPinger} packets on IPv6
 * multicast. Most home LANs do not forward IPv6 multicast, and Vanilla/Fabric clients only
 * listen on the IPv4 group, so the room never shows up for anyone (direct-IP joins still work).
 *
 * <p>We force the Vanilla IPv4 group so discovery behaves exactly like Vanilla and stays
 * compatible with Vanilla/Fabric clients on the same network.
 *
 * <p>{@code remap = false} because {@link DualStackUtils} is a NeoForge class, not a mapped
 * Minecraft class.
 */
@Mixin(value = DualStackUtils.class, remap = false)
public class DualStackForceIPv4Mixin {

    @Inject(method = "getMulticastGroup", at = @At("HEAD"), cancellable = true)
    private static void exbingo$forceIPv4MulticastGroup(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue("224.0.2.60");
    }
}
