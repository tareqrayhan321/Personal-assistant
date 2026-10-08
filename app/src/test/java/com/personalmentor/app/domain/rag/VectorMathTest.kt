package com.personalmentor.app.domain.rag

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class VectorMathTest {

    @Test fun normalizeGivesUnitLength() {
        val v = VectorMath.normalize(floatArrayOf(3f, 4f))
        assertEquals(1f, VectorMath.dot(v, v), 1e-6f)
        assertEquals(0.6f, v[0], 1e-6f)
    }

    @Test fun zeroVectorIsUnchanged() {
        assertArrayEquals(floatArrayOf(0f, 0f), VectorMath.normalize(floatArrayOf(0f, 0f)), 0f)
    }

    @Test fun dotOfOrthogonalVectorsIsZero() {
        assertEquals(0f, VectorMath.dot(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun dotRejectsDimensionMismatch() {
        VectorMath.dot(floatArrayOf(1f), floatArrayOf(1f, 2f))
    }

    @Test fun blobRoundTrip() {
        val v = floatArrayOf(0.25f, -1.5f, 3.14159f, 0f)
        assertArrayEquals(v, VectorMath.fromBlob(VectorMath.toBlob(v)), 0f)
    }

    @Test fun topKKeepsBestFirst() {
        val top = TopK<String>(2)
        listOf(0.1f to "a", 0.9f to "b", 0.5f to "c", 0.7f to "d").forEach { (s, x) -> top.add(s, x) }
        assertEquals(listOf("b", "d"), top.results().map { it.second })
    }

    @Test fun topKWithZeroCapacityIsEmpty() {
        val top = TopK<String>(0)
        top.add(1f, "a")
        assertEquals(emptyList<Any>(), top.results())
    }
}
