package com.official.woozi.abtoggle.hash

import org.apache.commons.codec.digest.MurmurHash3

/** MurmurHash3 x64 128비트, seed 0. 첫 번째 64비트(h1)를 부호 없는 값으로 사용한다. */
object MurmurHash3Function : HashFunction {
    override fun hash(input: ByteArray): ULong =
        MurmurHash3.hash128x64(input, 0, input.size, 0)[0].toULong()
}
