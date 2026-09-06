package com.privacygate.app.ai.gemma

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GemmaModelLocatorTest {
    @Test
    fun distinguishesMissingUndersizedAndReadyModel() {
        val root = Files.createTempDirectory("gemma-locator").toFile()
        val locator = GemmaModelLocator(root, minimumModelBytes = 4L)

        assertEquals(GemmaModelAvailability.Missing, locator.availability())

        val model = File(root, GemmaModelLocator.MODEL_FILE_NAME)
        model.writeBytes(byteArrayOf(1, 2, 3))
        assertTrue(locator.availability() is GemmaModelAvailability.Invalid)

        model.appendBytes(byteArrayOf(4))
        val ready = locator.availability()
        assertTrue(ready is GemmaModelAvailability.Ready)
        assertEquals(model.canonicalPath, (ready as GemmaModelAvailability.Ready).file.canonicalPath)
        root.deleteRecursively()
    }
}
