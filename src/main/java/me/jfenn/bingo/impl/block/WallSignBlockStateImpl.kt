package me.jfenn.bingo.impl.block

import me.jfenn.bingo.platform.block.IWallSignBlockState
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.WallSignBlock
import org.joml.Vector3i

class WallSignBlockStateImpl(
    private val blockState: BlockState,
) : BlockStateImpl(blockState), IWallSignBlockState {
    override val facing: Vector3i
        get() = blockState.getValue(WallSignBlock.FACING)
            .normal
            .let { Vector3i(it.x, it.y, it.z) }
}
