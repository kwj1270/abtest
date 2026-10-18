package com.official.woozi.abtoggle.hash

/**
 * 같은 입력에는 항상 같은 부호 없는 64비트 값을 반환한다.
 * 구현의 알고리즘, seed, 바이트 해석 규약을 변경하면 대상 배정도 바뀐다.
 * 여러 스레드에서 동시에 호출할 수 있어야 한다.
 */
fun interface HashFunction {
    fun hash(input: ByteArray): ULong
}
