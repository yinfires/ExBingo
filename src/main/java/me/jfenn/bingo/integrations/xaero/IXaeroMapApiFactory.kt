package me.jfenn.bingo.integrations.xaero

import me.jfenn.bingo.platform.IModEnvironment

interface IXaeroMapApiFactory {
    fun create(environment: IModEnvironment): IXaeroMapApi?
}
