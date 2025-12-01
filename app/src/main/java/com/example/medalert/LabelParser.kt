package com.example.medalert

object LabelParser {

    fun parse(text: String): MedicationEntry? {
        val lines = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null

        val patientName = lines.firstOrNull {
            it.startsWith("Patient:", true) || it.startsWith("Name:", true)
        }?.substringAfter(':')?.trim()
            ?: guessPatientName(lines)

        val directionStartIndex = lines.indexOfFirst {
            it.startsWith("take", true) ||
                    it.startsWith("sig", true) ||
                    it.startsWith("directions", true)
        }

        val directions = if (directionStartIndex != -1) {
            lines.drop(directionStartIndex)
                .takeWhile { it.isNotBlank() }
                .joinToString(" ")
                .lowercase()
        } else {
            ""
        }

        val timesPerDay = extractTimesPerDay(directions)

        /*val drugLine = lines.firstOrNull {
            Regex("""\b(\d+(\.\d+)?)\s*(mg|mcg|g|ml)\b""", RegexOption.IGNORE_CASE).containsMatchIn(it)
        } ?: lines.firstOrNull {
            it.contains("tablet", true) || it.contains("capsule", true) || it.contains("tab", true) || it.contains("cap", true)
        } ?: lines.firstOrNull { it.matches(Regex("""[A-Za-z][A-Za-z0-9\-\s]+""")) }*/
        // Find the line that likely contains the drug + strength
        val drugLine = lines.firstOrNull {
            Regex("""\b(\d+(\.\d+)?)\s*(mg|mcg|g|ml)\b""", RegexOption.IGNORE_CASE)
                .containsMatchIn(it)
        } ?: lines.firstOrNull {
            it.contains("tablet", true) || it.contains("capsule", true) ||
                    it.contains("tab", true) || it.contains("cap", true)
        } ?: lines.firstOrNull { it.matches(Regex("""[A-Za-z][A-Za-z0-9\-\s]+""")) }


        val strength = drugLine?.let {
            Regex("""\b(\d+(\.\d+)?)\s*(mg|mcg|g|ml)\b""", RegexOption.IGNORE_CASE).find(it)?.value
        }


        val form = drugLine?.let {
            Regex("""\b(tablet|tab|capsule|cap|solution|suspension|syrup|cream|ointment|patch|inhaler)\b""",
                RegexOption.IGNORE_CASE).find(it)?.value?.lowercase()
        }

        /*val drugName = drugLine
            ?.replace(Regex("""^(Rx\s*Only|Rx)\s*:?""", RegexOption.IGNORE_CASE), "")
            ?.replace(Regex("""\b\d+.*"""), "")   // remove numbers and everything after
            ?.trim()
            ?: return null
        // Extract drug name: take text BEFORE the first dosage pattern (e.g., "20 mg")
        val drugName = drugLine
            ?.let {
                val parts = it.split(Regex("""\b\d+(\.\d+)?\s*(mg|mcg|g|ml)\b""", RegexOption.IGNORE_CASE))
                parts.firstOrNull()?.trim()
            }
            ?.split(" ")          // Break into tokens
            ?.filter { it.isNotBlank() }
            ?.take(2)             // Allow 1 or 2-word drug names
            ?.joinToString(" ")
            ?.replace(Regex("""[^A-Za-z]"""), "")  // Remove weird characters
            ?.trim()
            ?: return null*/


// ---- DRUG NAME EXTRACTION + NORMALIZATION ----
        val drugName = drugLine
            // Take only the part BEFORE the first strength like "20 mg"
            ?.let { line ->
                val parts = line.split(
                    Regex("""\b\d+(\.\d+)?\s*(mg|mcg|g|ml)\b""", RegexOption.IGNORE_CASE)
                )
                parts.firstOrNull()?.trim()
            }
            // Split into words
            ?.split(Regex("\\s+"))
            ?.filter { it.isNotBlank() }
            ?.let { tokens ->
                // Common release / form suffixes that we want to drop from the END
                val suffixes = setOf(
                    "er", "xr", "sr", "cr", "dr", "ir", "xl", "la",
                    "cd", "cr", "hcl", "tab", "tablet", "cap", "capsule"
                )

                // If last token is in suffix list, drop it (e.g., "Verapamil ER" -> "Verapamil")
                val cleaned = if (tokens.size >= 2 && tokens.last().lowercase() in suffixes) {
                    tokens.dropLast(1)
                } else {
                    tokens
                }

                // Allow up to 3-word base names (e.g., "Amoxicillin Clavulanate")
                cleaned.take(3)
            }
            // Join back into a name
            ?.joinToString(" ")
            // Strip non-letter characters
            ?.replace(Regex("""[^A-Za-z\s]"""), "")
            ?.trim()
        // Ensure non-null for MedicationEntry (drugName is String, not String?)
            ?: return null



        val rxNumber = lines.firstOrNull { it.startsWith("Rx", true) && it.contains("#") }
            ?.substringAfter('#')?.trim()

        return MedicationEntry(
            patientName = patientName,
            drugName = drugName,
            directions = directions.ifEmpty { "See label" },
            rxNumber = rxNumber,
            strength = strength,
            timesPerDay = timesPerDay,
            form = form
        )
    }

    private fun extractTimesPerDay(dir: String): Int? {
        val text = dir.lowercase()

        when {
            Regex("""\btwice\b""").containsMatchIn(text) -> return 2
            Regex("""\btwo\s+times\b""").containsMatchIn(text) -> return 2
            Regex("""\b2\s+times\b""").containsMatchIn(text) -> return 2
            Regex("""\bonce\b""").containsMatchIn(text) -> return 1
            Regex("""\bthrice\b""").containsMatchIn(text) -> return 3
            Regex("""\bthree\s+times\b""").containsMatchIn(text) -> return 3
            Regex("""\b3\s+times\b""").containsMatchIn(text) -> return 3
        }


        return 1
    }

    private fun guessPatientName(lines: List<String>): String? {
        return lines.firstOrNull { it.matches(Regex("""^[A-Z][A-Z\s\.,'-]{3,}$""")) } // ALL CAPS
            ?: lines.firstOrNull { it.matches(Regex("""^[A-Z][a-z]+,\s*[A-Z][a-z]+$""")) } // Last, First
    }
}
