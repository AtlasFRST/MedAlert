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

        val drugName = drugLine?.replace(Regex("""^(Rx\s*Only|Rx)\s*:?""", RegexOption.IGNORE_CASE), "")?.trim()
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

    private fun guessPatientName(lines: List<String>): String? {
        return lines.firstOrNull { it.matches(Regex("""^[A-Z][A-Z\s\.,'-]{3,}$""")) } // ALL CAPS
            ?: lines.firstOrNull { it.matches(Regex("""^[A-Z][a-z]+,\s*[A-Z][a-z]+$""")) } // Last, First
    }
}
