package com.example.llmcar.data

object HfUrlParser {

    data class Resolved(val url: String, val fileName: String)

    fun parse(input: String): Result<Resolved> {
        val t = input.trim()
        if (t.isEmpty()) return Result.failure(IllegalArgumentException("Пустой ввод"))

        if (t.startsWith("http://") || t.startsWith("https://")) {
            val n = t.replace("/blob/", "/resolve/")
            val f = n.substringBefore('?').substringAfterLast('/')
            if (!f.endsWith(".gguf", true))
                return Result.failure(IllegalArgumentException("URL должен указывать на .gguf"))
            val sep = if (n.contains('?')) "&" else "?"
            val url = if (n.contains("download=true")) n else "$n${sep}download=true"
            return Result.success(Resolved(url, f))
        }

        val parts = t.split('/')
        if (parts.size >= 3 && parts.last().endsWith(".gguf", true)) {
            val (user, repo) = parts
            val f = parts.drop(2).joinToString("/")
            return Result.success(Resolved(
                "https://huggingface.co/$user/$repo/resolve/main/$f?download=true",
                f.substringAfterLast('/')))
        }
        return Result.failure(IllegalArgumentException("Неверный формат"))
    }
}