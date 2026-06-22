package me.jfenn.bingo.impl.block

import me.jfenn.bingo.impl.BlockRegistryEntry
import me.jfenn.bingo.platform.IRegistryEntry
import me.jfenn.bingo.platform.IServerWorld
import me.jfenn.bingo.platform.block.BlockPosition
import me.jfenn.bingo.platform.block.IBlockState
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.WallSignBlock
import net.minecraft.core.registries.BuiltInRegistries

open class BlockStateImpl(
    private val blockState: BlockState,
) : IBlockState {
    override val block: IRegistryEntry.Block get() = BlockRegistryEntry(
        BuiltInRegistries.BLOCK.wrapAsHolder(blockState.block)
    )

    override val identifier: String get() = BuiltInRegistries.BLOCK.getKey(blockState.block).toString()

    override fun isEmpty(world: IServerWorld, pos: BlockPosition): Boolean {
        return blockState.isAir || blockState.getCollisionShape(world.world, pos.toBlockPos()).isEmpty
    }

    override val isFluid: Boolean
        get() = blockState.block is LiquidBlock

    companion object {
        fun fromBlockState(blockState: BlockState): IBlockState {
            return when (blockState.block) {
                is WallSignBlock -> WallSignBlockStateImpl(blockState)
                else -> BlockStateImpl(blockState)
            }
        }
    }
}
