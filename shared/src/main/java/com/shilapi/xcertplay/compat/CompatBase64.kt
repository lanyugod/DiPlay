package com.shilapi.xcertplay.compat

/** RFC 4648 basic and Java MIME semantics, without API 26 java.util.Base64. */
object CompatBase64 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    fun getEncoder() = Encoder()
    fun getDecoder() = Decoder(false)
    fun getMimeDecoder() = Decoder(true)
    fun getMimeEncoder(lineLength: Int, separator: ByteArray): Encoder {
        require(separator.none { it.toInt().toChar() == '=' || ALPHABET.indexOf(it.toInt().toChar()) >= 0 })
        return Encoder((lineLength.coerceAtLeast(0) / 4) * 4, separator.toString(Charsets.ISO_8859_1))
    }

    class Encoder internal constructor(private val lineLength: Int = 0, private val separator: String = "") {
        fun encodeToString(bytes: ByteArray): String {
            val out = StringBuilder()
            var column = 0
            fun emit(c: Char) {
                if (lineLength > 0 && column == lineLength) { out.append(separator); column = 0 }
                out.append(c); column++
            }
            var i = 0
            while (i < bytes.size) {
                val a = bytes[i++].toInt() and 255
                val b = if (i < bytes.size) bytes[i++].toInt() and 255 else -1
                val c = if (i < bytes.size) bytes[i++].toInt() and 255 else -1
                emit(ALPHABET[a ushr 2])
                emit(ALPHABET[((a and 3) shl 4) or (if (b < 0) 0 else b ushr 4)])
                emit(if (b < 0) '=' else ALPHABET[((b and 15) shl 2) or (if (c < 0) 0 else c ushr 6)])
                emit(if (c < 0) '=' else ALPHABET[c and 63])
            }
            return out.toString()
        }
    }

    class Decoder internal constructor(private val mime: Boolean) {
        fun decode(encoded: ByteArray): ByteArray = decode(encoded.toString(Charsets.ISO_8859_1))
        fun decode(encoded: String): ByteArray {
            val output = java.io.ByteArrayOutputStream()
            var bits = 0
            var count = 0
            var index = 0
            while (index < encoded.length) {
                val character = encoded[index++]
                val value = ALPHABET.indexOf(character)
                if (value >= 0) {
                    bits = (bits shl 6) or value
                    count++
                    if (count == 4) {
                        output.write(bits ushr 16); output.write(bits ushr 8); output.write(bits)
                        bits = 0; count = 0
                    }
                } else if (character == '=') {
                    require(count == 2 || count == 3) { "Invalid Base64 padding" }
                    if (count == 2) {
                        // Like Java, the second padding byte must immediately follow the first.
                        require(index < encoded.length && encoded[index++] == '=') { "Invalid Base64 padding" }
                        output.write(bits ushr 4)
                    } else {
                        output.write(bits ushr 10); output.write(bits ushr 2)
                    }
                    while (index < encoded.length) {
                        val tail = encoded[index++]
                        require(mime && ALPHABET.indexOf(tail) < 0) { "Unexpected Base64 trailing data" }
                    }
                    return output.toByteArray()
                } else require(mime) { "Invalid Base64 alphabet" }
            }
            require(count != 1) { "Invalid Base64 length" }
            if (count == 2) output.write(bits ushr 4)
            if (count == 3) { output.write(bits ushr 10); output.write(bits ushr 2) }
            return output.toByteArray()
        }
    }
}
