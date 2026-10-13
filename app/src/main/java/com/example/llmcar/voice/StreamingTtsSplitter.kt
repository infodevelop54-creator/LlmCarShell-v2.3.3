package com.example.llmcar.voice

class StreamingTtsSplitter {

    private val buffer = StringBuilder()
    private val boundaries = charArrayOf('.', '!', '?', '\n', '…')

    fun feed(chunk: String): List<String> {
        if (chunk.isEmpty()) return emptyList()
        buffer.append(chunk)
        val sentences = mutableListOf<String>()
        var lastCut = 0
        for (i in buffer.indices) {
            if (buffer[i] in boundaries) {
                val s = buffer.substring(lastCut, i + 1).trim()
                if (s.isNotEmpty()) sentences.add(s)
                lastCut = i + 1
            }
        }
        if (lastCut > 0) buffer.delete(0, lastCut)
        return sentences
    }

    fun flush(): String {
        val s = buffer.toString().trim()
        buffer.clear()
        return s
    }

    fun reset() = buffer.clear()
}