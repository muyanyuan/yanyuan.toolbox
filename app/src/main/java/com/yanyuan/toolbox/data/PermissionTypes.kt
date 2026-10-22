package com.yanyuan.toolbox.data

sealed class PermissionRequirement {
    abstract val label: String
    abstract val description: String

    data class Runtime(
        val permission: String,
        override val label: String,
        override val description: String
    ) : PermissionRequirement()

    data class Special(
        val type: SpecialType,
        override val label: String,
        override val description: String
    ) : PermissionRequirement()
}

enum class SpecialType {
    ACCESSIBILITY,
    OVERLAY,
    BATTERY_OPTIMIZATION,
    USAGE_STATS
}