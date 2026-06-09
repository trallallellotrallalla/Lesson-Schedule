package com.shedule2

import org.json.JSONObject

data class PerformanceStage(
    val quantity: Int,
    val currentPoints: Int?,
    val maxPoints: Int
)

data class PerformanceWork(
    val name: String,
    val testName: String,
    val weekNumber: String,
    val currentPoints: String?,
    val maxPoints: String
) {
    val isChecked: Boolean get() = !currentPoints.isNullOrBlank()
}

data class PerformanceSubject(
    val name: String,
    val currentPoints: String,
    val maxPoints: String,
    val passed: PerformanceStage,
    val debt: PerformanceStage,
    val forward: PerformanceStage,
    val works: List<PerformanceWork>
)

data class PerformanceData(
    val subjects: List<PerformanceSubject>,
    val truancyAll: Int?,
    val truancyJustified: Int?
)

object PerformanceParser {

    fun parse(rawJson: String): PerformanceData {
        val root = JSONObject(rawJson)
        if (!root.optBoolean("success", false)) return PerformanceData(emptyList(), null, null)

        val result = root.optJSONObject("result") ?: return PerformanceData(emptyList(), null, null)
        val perf = result.optJSONArray("performance")

        val subjects = buildList {
            if (perf != null) {
                for (i in 0 until perf.length()) {
                    val s = perf.optJSONObject(i) ?: continue
                    val stages = s.optJSONObject("stages")
                    add(
                        PerformanceSubject(
                            name = s.optString("name").trim().trimEnd('.'),
                            currentPoints = s.optString("currentPoints").orEmpty(),
                            maxPoints = s.optString("maxPoints").orEmpty(),
                            passed = parseStage(stages?.optJSONObject("passed")),
                            debt = parseStage(stages?.optJSONObject("debt")),
                            forward = parseStage(stages?.optJSONObject("forward")),
                            works = parseWorks(s.optJSONArray("works"))
                        )
                    )
                }
            }
        }

        val truancy = result.optJSONObject("truancy")
        val truancyAll = truancy?.optInt("all", -1)?.takeIf { it >= 0 }
        val truancyJustified = truancy?.optInt("justified", -1)?.takeIf { it >= 0 }

        return PerformanceData(subjects, truancyAll, truancyJustified)
    }

    private fun parseStage(obj: JSONObject?): PerformanceStage {
        if (obj == null) return PerformanceStage(0, null, 0)
        val cur = if (obj.isNull("currentPoints")) null else obj.optInt("currentPoints", 0)
        return PerformanceStage(
            quantity = obj.optInt("quantity", 0),
            currentPoints = cur,
            maxPoints = obj.optInt("maxPoints", 0)
        )
    }

    private fun parseWorks(arr: org.json.JSONArray?): List<PerformanceWork> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val w = arr.optJSONObject(i) ?: continue
                val cur = if (w.isNull("currentPoints")) null else w.optString("currentPoints").ifBlank { null }
                add(
                    PerformanceWork(
                        name = w.optString("name").orEmpty(),
                        testName = w.optString("testName").orEmpty(),
                        weekNumber = w.optString("weekNumber").orEmpty(),
                        currentPoints = cur,
                        maxPoints = w.optString("maxPoints").orEmpty()
                    )
                )
            }
        }
    }
}
