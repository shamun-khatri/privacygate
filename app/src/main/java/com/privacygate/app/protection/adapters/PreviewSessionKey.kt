package com.privacygate.app.protection.adapters

object PreviewSessionKey {
    fun create(
        packageName: String,
        windowId: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): String = "$packageName:$windowId:$left,$top,$right,$bottom"
}
