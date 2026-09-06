package com.privacygate.app.protection

object ProtectionTargetPolicy {
    val packages: Set<String> = setOf("com.whatsapp", "com.whatsapp.w4b")

    fun accepts(packageName: String): Boolean = packageName in packages
}
