package com.hongxiang.kpilot.data

enum class NavigationItem {
    LIBRARY, NOTES, REVIEW, VOCABULARY, REMOTE, SEND, SETTINGS
}

object NavigationOptions {
    val required = setOf(NavigationItem.NOTES, NavigationItem.LIBRARY, NavigationItem.SETTINGS)
    val defaults = setOf(NavigationItem.LIBRARY, NavigationItem.NOTES, NavigationItem.VOCABULARY, NavigationItem.REMOTE, NavigationItem.SETTINGS)
    const val maximumItems = 5

    fun decode(stored: Set<String>?): Set<NavigationItem> {
        if (stored == null) return defaults
        val optional = NavigationItem.entries.filter { it in setOf(NavigationItem.REMOTE, NavigationItem.VOCABULARY) && it.name in stored }
        return required + optional.take(maximumItems - required.size)
    }

    fun update(current: Set<NavigationItem>, item: NavigationItem, visible: Boolean): Set<NavigationItem> {
        val normalized = decode(current.map { it.name }.toSet())
        if (item !in setOf(NavigationItem.REMOTE, NavigationItem.VOCABULARY)) return normalized
        return if (visible) normalized + item else normalized - item
    }
}
