package com.shedule2

import org.json.JSONArray
import org.json.JSONObject

object EduParser {

    fun parseCourseIdsFromAjax(json: String): List<Int> {
        return runCatching {
            val root = JSONArray(json)
            val ids = mutableListOf<Int>()
            for (i in 0 until root.length()) {
                val item = root.optJSONObject(i) ?: continue
                val data = item.optJSONObject("data") ?: continue
                val courses = data.optJSONArray("courses") ?: continue
                for (j in 0 until courses.length()) {
                    val course = courses.optJSONObject(j) ?: continue
                    val id = course.optInt("id", -1)
                    if (id > 0) ids += id
                }
            }
            ids.distinct()
        }.getOrDefault(emptyList())
    }

    private val courseIdPatterns = listOf(
        Regex("""course/view\.php\?id=(\d+)"""),
        Regex("""data-course-id=["'](\d+)["']"""),
        Regex("""courseid["']?\s*[:=]\s*["']?(\d+)""")
    )
    private val titlePattern = Regex("""<h1[^>]*class="h2"[^>]*>([\s\S]*?)</h1>""")
    private val trPattern = Regex("""<tr\b[^>]*>([\s\S]*?)</tr>""")
    private val gradeCellPattern = Regex("""<td[^>]*column-grade[^>]*>([\s\S]*?)</td>""")
    private val rangeCellPattern = Regex("""<td[^>]*column-range[^>]*>([\s\S]*?)</td>""")
    private val gradeItemNamePattern = Regex("""<a[^>]*class="gradeitemheader[^"]*"[^>]*>([\s\S]*?)</a>""")
    private val tagPattern = Regex("""<[^>]+>""")
    private val whitespacePattern = Regex("""\s+""")
    private val digitRunPattern = Regex("""\d+""")

    fun parseCourseIds(html: String): List<Int> {
        return courseIdPatterns
            .flatMap { pattern -> pattern.findAll(html).map { it.groupValues[1] } }
            .mapNotNull { it.toIntOrNull() }
            .distinct()
            .toList()
    }

    fun parseSubject(html: String, fallbackName: String): PerformanceSubject? {
        val rawTitle = titlePattern.find(html)?.groupValues?.get(1)
        val courseName = rawTitle?.let { cleanText(it) }
            ?.takeIf { it.isNotBlank() }
            ?: fallbackName

        var totalCurrent: String? = null
        var totalMax: String? = null
        val works = mutableListOf<PerformanceWork>()

        trPattern.findAll(html).forEach { trMatch ->
            val rowHtml = trMatch.groupValues[1]
            val gradeRaw = gradeCellPattern.find(rowHtml)?.groupValues?.get(1) ?: return@forEach
            val rangeRaw = rangeCellPattern.find(rowHtml)?.groupValues?.get(1) ?: return@forEach

            val grade = parsePoints(gradeRaw)
            val maxPoints = parseMaxFromRange(rangeRaw)

            val isTotal = rowHtml.contains("Итоговая оценка за курс") || rowHtml.contains("baggb")

            if (isTotal) {
                if (totalCurrent == null) {
                    totalCurrent = grade.orEmpty()
                    totalMax = maxPoints.orEmpty()
                }
                return@forEach
            }

            val nameRaw = gradeItemNamePattern.find(rowHtml)?.groupValues?.get(1)
            val workName = nameRaw?.let { cleanText(it) }
            if (workName.isNullOrBlank()) return@forEach

            works += PerformanceWork(
                name = "",
                testName = workName,
                weekNumber = "",
                currentPoints = grade,
                maxPoints = maxPoints.orEmpty()
            )
        }

        if (works.isEmpty() && totalCurrent.isNullOrBlank()) return null

        val passedCurrent = totalCurrent?.toIntOrNull()
        val totalMaxInt = totalMax?.toIntOrNull() ?: 0

        return PerformanceSubject(
            name = courseName,
            currentPoints = totalCurrent.orEmpty(),
            maxPoints = totalMax.orEmpty(),
            passed = PerformanceStage(
                quantity = works.count { it.isChecked },
                currentPoints = passedCurrent,
                maxPoints = totalMaxInt
            ),
            debt = PerformanceStage(0, null, 0),
            forward = PerformanceStage(0, null, 0),
            works = works
        )
    }

    private fun parsePoints(cellHtml: String): String? {
        val clean = cleanText(cellHtml)
        if (clean.isBlank() || clean == "-") return null
        return digitRunPattern.find(clean)?.value
    }

    private fun parseMaxFromRange(cellHtml: String): String? {
        val clean = cleanText(cellHtml)
        if (clean.isBlank() || clean == "-") return null
        return digitRunPattern.findAll(clean).map { it.value }.lastOrNull()
    }

    private fun cleanText(html: String): String {
        return tagPattern.replace(html, " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&ndash;", "-")
            .replace("&mdash;", "-")
            .replace("&#8211;", "-")
            .replace("&#8212;", "-")
            .let { whitespacePattern.replace(it, " ") }
            .trim()
    }
}
