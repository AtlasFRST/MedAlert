package com.example.medalert

object LabelParser {

    fun parse(text: String): MedicationEntry? {
        val lines = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null

        val patientName = lines.firstOrNull {
            it.startsWith("Patient:", true) || it.startsWith("Name:", true)
        }?.substringAfter(':')?.trim()
            ?: guessPatientName(lines)

        val directions = lines.firstOrNull {
            it.startsWith("Take", true) ||
                    it.startsWith("Sig", true) ||
                    it.startsWith("Directions", true) ||
                    it.contains("by mouth", true) ||
                    it.contains("po ", true)
        }?.let { line ->
            line.substringAfter(":", line).trim()
        } ?: ""

        val timesPerDay = extractTimesPerDay(directions)

        val drugLine = lines.firstOrNull {
            Regex("""\b(\d+(\.\d+)?)\s*(mg|mcg|g|ml)\b""", RegexOption.IGNORE_CASE).containsMatchIn(it)
        } ?: lines.firstOrNull {
            it.contains("tablet", true) || it.contains("capsule", true) || it.contains("tab", true) || it.contains("cap", true)
        } ?: lines.firstOrNull { it.matches(Regex("""[A-Za-z][A-Za-z0-9\-\s]+""")) }

        val strength = drugLine?.let {
            Regex("""\b(\d+(\.\d+)?)\s*(mg|mcg|g|ml)\b""", RegexOption.IGNORE_CASE).find(it)?.value
        }

        val form = drugLine?.let {
            Regex("""\b(tablet|tab|capsule|cap|solution|suspension|syrup|cream|ointment|patch|inhaler)\b""",
                RegexOption.IGNORE_CASE).find(it)?.value?.lowercase()
        }

        val drugName = drugLine
            ?.replace(Regex("""^(Rx\s*Only|Rx)\s*:?""", RegexOption.IGNORE_CASE), "")
            ?.replace(Regex("""\b\d+.*"""), "")   // remove numbers and everything after
            ?.trim()
            ?: return null

        val rxNumber = lines.firstOrNull { it.startsWith("Rx", true) && it.contains("#") }
            ?.substringAfter('#')?.trim()

        return MedicationEntry(
            patientName = patientName,
            drugName = drugName,
            directions = directions.ifEmpty { "See label" },
            rxNumber = rxNumber,
            strength = strength,
            form = form
        )
    }

    private fun extractTimesPerDay(dir: String): Int? {
        val text = dir.lowercase()

        // 1. numbers + "time(s)" + optional daily context
        Regex("""(\d+)\s*times?\s*(a\s*day|daily|per\s*day)?""").find(text)?.let {
        return it.groupValues[1].toInt()
        }

        // 2. "take 3x/day", "3x per day"
        Regex("""(\d+)\s*x\s*/\s*day""").find(text)?.let {
            return it.groupValues[1].toInt()
        }

        // 3. Words: once, twice, thrice
        when {
            text.contains("once") || text.contains("once daily") || text.contains("qday")  || text.contains("once per day")-> return 1
            text.contains("twice") || text.contains("two times") -> return 2
            text.contains("thrice") || text.contains("three times") -> return 3
        }

        // 4. Medical abbreviations
        when {
            text.contains("qd") -> return 1
            text.contains("bid") -> return 2
            text.contains("tid") -> return 3
            text.contains("qid") -> return 4
        }

        // 5. q12h, q8h, q6h → convert hours to times/day
        Regex("""q(\d+)\s*h""").find(text)?.let {
            val hours = it.groupValues[1].toInt()
            if (hours > 0) return 24 / hours
        }

        return 1
    }

    private fun guessPatientName(lines: List<String>): String? {
        return lines.firstOrNull { it.matches(Regex("""^[A-Z][A-Z\s\.,'-]{3,}$""")) } // ALL CAPS
            ?: lines.firstOrNull { it.matches(Regex("""^[A-Z][a-z]+,\s*[A-Z][a-z]+$""")) } // Last, First
    }
}
