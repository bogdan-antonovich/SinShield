package app.sinshield

import java.io.BufferedReader
import java.net.IDN

/** Immutable suffix matcher for hosts-file and one-domain-per-line blocklists. */
internal class AdultDomainMatcher private constructor(
    private val domains: Set<String>
) {
    val size: Int
        get() = domains.size

    fun isBlocked(hostname: String): Boolean {
        // Walk parent suffixes: a blocked "example.com" also covers "cdn.images.example.com".
        // Strip the leftmost label each iteration until a suffix is in the set or none remain.
        var candidate = normalize(hostname) ?: return false
        while (true) {
            if (candidate in domains) return true
            val dot = candidate.indexOf('.')
            if (dot < 0) return false
            candidate = candidate.substring(dot + 1)
        }
    }

    fun withDomains(additionalDomains: Iterable<String>): AdultDomainMatcher {
        val additions = additionalDomains.mapNotNull(::normalize)
        return if (additions.isEmpty()) this else AdultDomainMatcher(domains + additions)
    }

    companion object {
        // Hosts-file columns are separated by runs of spaces/tabs. Compiled once here rather than
        // rebuilt per line so parsing a 70k+ entry blocklist doesn't recompile the pattern on
        // every row (the dominant allocation in the old parse loop).
        private val COLUMN_SEPARATOR = Regex("\\s+")

        fun empty(): AdultDomainMatcher = AdultDomainMatcher(emptySet())

        fun fromReader(reader: BufferedReader): AdultDomainMatcher =
            fromLines(reader.lineSequence())

        fun fromLines(lines: Sequence<String>): AdultDomainMatcher {
            val parsed = HashSet<String>()
            lines.forEach { line ->
                // Strip "#" comments; the maintained blocklist is a hosts file with header notes.
                val content = line.substringBefore('#').trim()
                if (content.isEmpty()) return@forEach
                val fields = content.split(COLUMN_SEPARATOR).filter(String::isNotEmpty)
                if (fields.isEmpty()) return@forEach

                // Hosts-file lines are "0.0.0.0 blocked.example"; the leading address is not a
                // domain, so it is dropped and the rest are hostnames. A line that is a bare
                // hostname (one-domain-per-line lists) is taken as-is.
                val candidates = if (looksLikeAddress(fields.first())) {
                    fields.drop(1)
                } else {
                    listOf(fields.first())
                }
                candidates.forEach { candidate ->
                    normalize(candidate)?.let(parsed::add)
                }
            }
            return AdultDomainMatcher(parsed)
        }

        /** Strict validation for domains entered by a user rather than trusted blocklist lines. */
        fun normalizeUserDomain(value: String): String? {
            val normalized = normalize(value) ?: return null
            val labels = normalized.split('.')
            if (labels.size < 2 || labels.any { label ->
                    label.isEmpty() || label.length > 63 || label.startsWith('-') ||
                        label.endsWith('-') || label.any { !it.isLetterOrDigit() && it != '-' }
                }
            ) {
                return null
            }
            return normalized.takeUnless { labels.last().all(Char::isDigit) }
        }

        private fun normalize(value: String): String? {
            // Drop the trailing root dot and lowercase so lookups and stored entries share one form.
            val hostname = value.trim().trimEnd('.').lowercase()
            // "localhost" and raw addresses are hosts-file redirect targets, not domains to block.
            if (hostname.isEmpty() || hostname == "localhost" || looksLikeAddress(hostname)) {
                return null
            }
            // Fold internationalized names to their punycode (ASCII) form so a Unicode hostname and
            // its encoded query resolve to the same key. Require a dot and the 253-char DNS name
            // limit to discard single-label junk and over-long lines.
            return runCatching { IDN.toASCII(hostname) }
                .getOrNull()
                ?.takeIf { it.length <= 253 && '.' in it }
        }

        private fun looksLikeAddress(value: String): Boolean =
            value.contains(':') || value.all { it.isDigit() || it == '.' }
    }
}
