package com.official.woozi.abtoggle.hash

import java.nio.ByteBuffer
import java.security.MessageDigest

/** SHA-256의 앞 8바이트를 big-endian 부호 없는 64비트 정수로 읽는다. */
object Sha256HashFunction : HashFunction {
    override fun hash(input: ByteArray): ULong {
        val digest = MessageDigest.getInstance("SHA-256").digest(input)
        return ByteBuffer.wrap(digest, 0, Long.SIZE_BYTES).getLong().toULong()
    }
}
