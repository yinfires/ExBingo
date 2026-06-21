package me.jfenn.bingo.common.utils

import net.minecraft.nbt.DoubleTag
import net.minecraft.nbt.FloatTag
import net.minecraft.nbt.ListTag
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3d

fun Vec3.toNbt(): ListTag {
    return ListTag().apply {
        add(DoubleTag.valueOf(x))
        add(DoubleTag.valueOf(y))
        add(DoubleTag.valueOf(z))
    }
}

fun Vector3d.toNbt(): ListTag {
    return ListTag().apply {
        add(DoubleTag.valueOf(x))
        add(DoubleTag.valueOf(y))
        add(DoubleTag.valueOf(z))
    }
}

fun List<Float>.toNbt(): ListTag {
    val list = ListTag()
    forEach { list.add(FloatTag.valueOf(it)) }
    return list
}

/**
 * Formats into a row-major list of NBT floats
 */
fun Matrix4f.toNbt(): ListTag {
    val list = ListTag()
    for (row in 0..3) for (col in 0..3) {
        list.add(FloatTag.valueOf(this.get(col, row)))
    }
    return list
}
