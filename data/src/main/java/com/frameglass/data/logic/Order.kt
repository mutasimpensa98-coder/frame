package com.frameglass.data.logic

/** Case-insensitive natural order: "E2" sorts before "E10". */
internal object NaturalOrder : Comparator<String> {
    private fun digit(c: Char): Int = Character.digit(c, 10)

    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (digit(ca) >= 0 && digit(cb) >= 0) {
                val startA = i
                val startB = j
                while (i < a.length && digit(a[i]) >= 0) i++
                while (j < b.length && digit(b[j]) >= 0) j++
                val cmp = compareNumbers(a, startA, i, b, startB, j)
                if (cmp != 0) return cmp
            } else {
                val cmp = Character.toLowerCase(ca).compareTo(Character.toLowerCase(cb))
                if (cmp != 0) return cmp
                i++
                j++
            }
        }
        return (a.length - i).compareTo(b.length - j)
    }

    private fun compareNumbers(a: String, s1: Int, e1: Int, b: String, s2: Int, e2: Int): Int {
        var x = s1
        var y = s2
        while (x < e1 - 1 && digit(a[x]) == 0) x++
        while (y < e2 - 1 && digit(b[y]) == 0) y++
        if (e1 - x != e2 - y) return (e1 - x).compareTo(e2 - y)
        while (x < e1) {
            val cmp = digit(a[x]).compareTo(digit(b[y]))
            if (cmp != 0) return cmp
            x++
            y++
        }
        return 0
    }
}

/**
 * The walk visits directories depth first with children sorted by name, and a scan checkpoint names the
 * last directory whose own files are done. These functions compare paths in that same order, segment by
 * segment, because plain string order puts "a b" before "a/b".
 */
internal object ScanOrder {
    fun compare(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return -1
        if (b.isEmpty()) return 1
        val left = a.split('/')
        val right = b.split('/')
        for (i in 0 until minOf(left.size, right.size)) {
            val cmp = left[i].compareTo(right[i])
            if (cmp != 0) return cmp
        }
        return left.size.compareTo(right.size)
    }

    /** True when this directory's own files were handled before the checkpoint. */
    fun isDone(dir: String, checkpoint: String?): Boolean = checkpoint != null && compare(dir, checkpoint) <= 0

    /** True when nothing under [dir] can come after the checkpoint, so the walk can skip the subtree. */
    fun canPrune(dir: String, checkpoint: String?): Boolean {
        if (checkpoint == null || dir.isEmpty()) return false
        return compare(dir, checkpoint) < 0 && !checkpoint.startsWith("$dir/")
    }
}
