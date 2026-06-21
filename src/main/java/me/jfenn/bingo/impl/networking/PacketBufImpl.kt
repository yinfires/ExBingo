package me.jfenn.bingo.impl.networking

import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.impl.TextImpl
import me.jfenn.bingo.platform.IPacketBuf
import me.jfenn.bingo.platform.item.IItemStack
import me.jfenn.bingo.platform.item.IItemStackFactory
import net.minecraft.world.item.ItemStack
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization

class PacketBufImpl(
    private val buf: RegistryFriendlyByteBuf,
    private val itemStackFactory: IItemStackFactory,
) : IPacketBuf {
    override fun writeString(str: String) = buf.writeUtf(str).let {}
    override fun readString(): String = buf.readUtf()

    override fun writeInt(int: Int) = buf.writeInt(int).let {}
    override fun readInt(): Int = buf.readInt()

    override fun writeLong(long: Long) = buf.writeLong(long).let {}
    override fun readLong(): Long = buf.readLong()

    override fun writeFloat(float: Float) = buf.writeFloat(float).let {}
    override fun readFloat(): Float = buf.readFloat()

    override fun writeBoolean(bool: Boolean) = buf.writeBoolean(bool).let {}
    override fun readBoolean(): Boolean = buf.readBoolean()

    override fun writeItemStack(stack: IItemStack) = ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack.stack)
    override fun readItemStack(): IItemStack = itemStackFactory.forStack(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf))

    override fun writeText(text: IText?) = ComponentSerialization.STREAM_CODEC.encode(buf, text?.value ?: Component.empty())
    override fun readText(): IText = ComponentSerialization.STREAM_CODEC.decode(buf).let { TextImpl(it.copy()) }

    override fun writeByteArray(array: ByteArray) = buf.writeByteArray(array).let {}
    override fun readByteArray(): ByteArray = buf.readByteArray()
}
