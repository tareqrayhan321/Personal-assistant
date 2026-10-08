package com.personalmentor.app.domain.rag

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.PriorityQueue
import kotlin.math.sqrt

object VectorMath {
    /** Unit-length copy, so cosine similarity reduces to a dot product. A zero vector is returned unchanged. */
    fun normalize(v: FloatArray): FloatArray {
        var sum = 0.0
        for (x in v) sum += x.toDouble() * x
        val norm = sqrt(sum).toFloat()
        if (norm == 0f) return v.copyOf()
        return FloatArray(v.size) { v[it] / norm }
    }

    fun dot(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Dimension mismatch: ${a.size} vs ${b.size}" }
        var s = 0f
        for (i in a.indices) s += a[i] * b[i]
        return s
    }

    fun toBlob(v: FloatArray): ByteArray {
        val buf = ByteBuffer.allocate(v.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        buf.asFloatBuffer().put(v)
        return buf.array()
    }

    fun fromBlob(bytes: ByteArray): FloatArray {
        val fb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(fb.remaining()).also { fb.get(it) }
    }
}

/** Keeps the [k] highest-scoring items seen so far. */
class TopK<T>(private val k: Int) {
    private val heap = PriorityQueue<Pair<Float, T>>(compareBy { it.first })

    fun add(score: Float, item: T) {
        if (k <= 0) return
        if (heap.size < k) heap.add(score to item)
        else if (score > heap.peek().first) {
            heap.poll()
            heap.add(score to item)
        }
    }

    /** Best first. */
    fun results(): List<Pair<Float, T>> = heap.sortedByDescending { it.first }
}
