package com.shilapi.xcertplay.compat

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64
import java.util.Random

class CompatBase64Test {
    @Test fun basicAndPemRemainByteIdenticalToJava() {
        val random = Random(23)
        for (size in listOf(0, 1, 2, 3, 47, 48, 49, 4096)) {
            val data = ByteArray(size).also(random::nextBytes)
            val encoded = Base64.getEncoder().encodeToString(data)
            assertEquals(encoded, CompatBase64.getEncoder().encodeToString(data))
            assertArrayEquals(data, CompatBase64.getDecoder().decode(encoded))
            assertArrayEquals(data, CompatBase64.getDecoder().decode(encoded.trimEnd('=')))
            val pem = Base64.getMimeEncoder(64, byteArrayOf(10)).encodeToString(data)
            assertEquals(pem, CompatBase64.getMimeEncoder(64, byteArrayOf(10)).encodeToString(data))
            assertArrayEquals(data, CompatBase64.getMimeDecoder().decode(pem))
        }
    }

    @Test fun mimeNoiseAndPaddingHaveJavaSemantics() {
        val samples = listOf("", "Zg", "Zg==", "Zg===", "Zg==!", "Z!g==", "Zg= =", "Zm8=", "Zm8==", "Zm9v", "Zm9v=", "A", "AA=", "=AAA", "A===", "Zm8=A", "éZg==", "Zm_8", " Z\ng==")
        for (mime in listOf(false, true)) for (sample in samples) {
            val javaResult = runCatching { (if (mime) Base64.getMimeDecoder() else Base64.getDecoder()).decode(sample) }
            val compatResult = runCatching { (if (mime) CompatBase64.getMimeDecoder() else CompatBase64.getDecoder()).decode(sample) }
            assertEquals("mime=$mime input=$sample", javaResult.isSuccess, compatResult.isSuccess)
            if (javaResult.isSuccess) assertArrayEquals(javaResult.getOrThrow(), compatResult.getOrThrow())
            else assertTrue(compatResult.exceptionOrNull() is IllegalArgumentException)
        }
    }
}
