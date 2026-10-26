package com.hongxiang.kpilot.data

enum class NavigationItem {
    LIBRARY, NOTES, REVIEW, VOCABULARY, REMOTE, SEND, SETTINGS
}

object NavigationOptions {
    val required = setOf(NavigationItem.NOTES, NavigationItem.SETTINGS)
    val defaults = setOf(NavigationItem.LIBRARY, NavigationItem.NOTES, NavigationItem.VOCABULARY, NavigationItem.REMOTE, NavigationItem.SETTINGS)
    const val maximumItems = 5

    fun decode(stored: Set<String>?): Set<NavigationItem> {
        if (stored == null) return defaults
        val optional = NavigationItem.entries.filter { it !in required && it.name in stored }
        return required + optional.take(maximumItems - required.size)
    }

    fun update(current: Set<NavigationItem>, item: NavigationItem, visible: Boolean): Set<NavigationItem> {
        if (item in required) return current + required
        if (!visible) return (current - item) + required
        return if (item in current || current.size < maximumItems) current + item + required else current
    }
}
