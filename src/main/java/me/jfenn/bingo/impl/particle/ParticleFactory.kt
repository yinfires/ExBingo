package me.jfenn.bingo.impl.particle

import me.jfenn.bingo.common.map.Color
import me.jfenn.bingo.impl.PlayerHandle
import me.jfenn.bingo.platform.IPlayerHandle
import me.jfenn.bingo.platform.particle.IParticle
import me.jfenn.bingo.platform.particle.IParticleFactory
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.server.level.ServerLevel
import org.joml.Vector3d
import org.joml.Vector3f

object ParticleFactory : me.jfenn.bingo.platform.particle.IParticleFactory {
    override fun createDustParticle(color: Int, scale: Float): me.jfenn.bingo.platform.particle.IParticle {
        val (r, g, b) = Color.fromInt(color)
        val effect = DustParticleOptions(Vector3f(r / 255f, g / 255f, b / 255f), scale)
        return ParticleImpl(effect)
    }

    class ParticleImpl(
        private val effect: ParticleOptions
    ) : me.jfenn.bingo.platform.particle.IParticle {
        override fun spawn(player: IPlayerHandle, position: Vector3d, count: Int, delta: Vector3d, speed: Double) {
            require(player is PlayerHandle)
            val serverWorld: ServerLevel = player.serverWorld
            serverWorld.sendParticles(
                player.player,
                effect,
                true,
                position.x,
                position.y,
                position.z,
                count,
                delta.x,
                delta.y,
                delta.z,
                speed
            )
        }
    }
}
