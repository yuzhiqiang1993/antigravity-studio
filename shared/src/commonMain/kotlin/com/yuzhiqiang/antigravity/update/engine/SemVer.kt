package com.yuzhiqiang.antigravity.update.engine

/**
 * 语义化版本号（Semantic Versioning）解析与比较器。
 * 支持标准 semver 格式（如 2.0.0, v2.1.3-beta.1, 1.0.0-rc2 等）。
 */
data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null,
    val raw: String
) : Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int {
        if (this.major != other.major) {
            return this.major.compareTo(other.major)
        }
        if (this.minor != other.minor) {
            return this.minor.compareTo(other.minor)
        }
        if (this.patch != other.patch) {
            return this.patch.compareTo(other.patch)
        }

        // 正式版本优先级高于 Pre-release 版本 (例如 2.0.0 > 2.0.0-beta.1)
        if (this.preRelease == null && other.preRelease != null) return 1
        if (this.preRelease != null && other.preRelease == null) return -1
        if (this.preRelease != null && other.preRelease != null) {
            return comparePreRelease(this.preRelease, other.preRelease)
        }

        return 0
    }

    private fun comparePreRelease(a: String, b: String): Int {
        val partsA = a.split(".", "-")
        val partsB = b.split(".", "-")
        val length = maxOf(partsA.size, partsB.size)
        for (i in 0 until length) {
            val pA = partsA.getOrNull(i)
            val pB = partsB.getOrNull(i)
            if (pA == null) return -1
            if (pB == null) return 1

            val numA = pA.toIntOrNull()
            val numB = pB.toIntOrNull()

            if (numA != null && numB != null) {
                val cmp = numA.compareTo(numB)
                if (cmp != 0) return cmp
            } else {
                val cmp = pA.compareTo(pB, ignoreCase = true)
                if (cmp != 0) return cmp
            }
        }
        return 0
    }

    companion object {
        private val SEMVER_REGEX = Regex(
            """^[vV]?(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:[-.]([0-9A-Za-z.-]+))?$"""
        )

        fun parse(versionStr: String): SemVer {
            val clean = versionStr.trim()
            val match = SEMVER_REGEX.find(clean)
            if (match == null) {
                // 回退保底解析
                val digits = clean.filter { it.isDigit() || it == '.' }.split('.').mapNotNull { it.toIntOrNull() }
                val major = digits.getOrElse(0) { 0 }
                val minor = digits.getOrElse(1) { 0 }
                val patch = digits.getOrElse(2) { 0 }
                return SemVer(major, minor, patch, preRelease = null, raw = clean)
            }

            val major = match.groupValues[1].toIntOrNull() ?: 0
            val minor = match.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
            val patch = match.groupValues.getOrNull(3)?.takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
            val preRelease = match.groupValues.getOrNull(4)?.takeIf { it.isNotEmpty() }

            return SemVer(major, minor, patch, preRelease, raw = clean)
        }

        fun isNewer(latest: String, current: String): Boolean {
            val semLatest = parse(latest)
            val semCurrent = parse(current)
            return semLatest > semCurrent
        }
    }
}
