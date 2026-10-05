package com.lionel.hub

/** Typo-tolerant app matching (prefix > word start > contains > letters-in-order > small typo). */
object Fuzzy {
    fun score(query: String, text: String): Int {
        val q = query.trim().lowercase()
        val t = text.lowercase()
        if (q.isEmpty() || t.isEmpty()) return 0
        if (t == q) return 100
        if (t.startsWith(q)) return 90
        val words = t.split(' ', '-', '_', '.').filter { it.isNotEmpty() }
        if (words.any { it.startsWith(q) }) return 80
        if (t.contains(q)) return 70
        if (q.length >= 2 && isSubsequence(q, t)) return 50
        if (q.length >= 4) {
            val d = (words + t).minOf { w -> levenshtein(q, w.take(q.length)) }
            if (d <= 1) return 45
            if (d == 2 && q.length >= 6) return 35
        }
        return 0
    }

    fun rank(query: String, apps: List<AppEntry>, limit: Int): List<AppEntry> =
        apps.map { it to maxOf(score(query, it.label), score(query, it.packageName) / 2) }
            .filter { it.second >= 35 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }

    private fun isSubsequence(q: String, t: String): Boolean {
        var i = 0
        for (c in t) {
            if (i < q.length && c == q[i]) i++
        }
        return i == q.length
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val tmp = prev
            prev = cur
            cur = tmp
        }
        return prev[b.length]
    }
}
