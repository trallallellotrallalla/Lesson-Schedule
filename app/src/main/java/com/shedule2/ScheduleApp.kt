package com.shedule2

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.DpOffset
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val NO_INTERNET_MESSAGE = "Нет подключения к интернету. Подключитесь к сети и попробуйте снова."

@Suppress("DEPRECATION")
private fun hasInternetConnection(context: Context): Boolean {
    return runCatching {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return@runCatching false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return@runCatching false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@runCatching false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            connectivityManager.activeNetworkInfo?.isConnectedOrConnecting == true
        }
    }.getOrDefault(false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val cookieJar = remember { SessionCookieJar(context) }
    val httpClient = remember { AuthApi.createClient(cookieJar) }
    val authApi = remember { AuthApi(httpClient) }
    val scheduleApi = remember { ScheduleApi(httpClient) }
    val performanceApi = remember { PerformanceApi(httpClient) }
    val eduApi = remember { EduApi(httpClient) }
    val captchaApi = remember(context, httpClient) {
        CaptchaApi(httpClient) { hasInternetConnection(context) }
    }

    var showPerformanceScreen by remember { mutableStateOf(false) }
    var performanceData by remember { mutableStateOf<PerformanceData?>(null) }
    var performanceLoading by remember { mutableStateOf(false) }
    var performanceError by remember { mutableStateOf<String?>(null) }

    var showEduScreen by remember { mutableStateOf(false) }
    var eduData by remember { mutableStateOf<PerformanceData?>(null) }
    var eduLoading by remember { mutableStateOf(false) }
    var eduError by remember { mutableStateOf<String?>(null) }

    val formatter = remember { DateTimeFormatter.ofPattern("d-M-yyyy") }
    val today = remember { LocalDate.now() }
    val initialCachedSchedule = remember { ScheduleCache.load(context) }
    val initialCachedLessons = remember(initialCachedSchedule?.rawJson) {
        initialCachedSchedule?.rawJson
            ?.let { rawJson -> runCatching { ScheduleParser.parseSimple(rawJson) }.getOrDefault(emptyList()) }
            ?: emptyList()
    }
    val initialCachedWeekDays = remember(initialCachedSchedule?.rawJson) {
        initialCachedSchedule?.rawJson
            ?.let { rawJson -> runCatching { parseWeekDays(rawJson) }.getOrDefault(emptyList()) }
            ?: emptyList()
    }
    val initialCachedWeekInfo = remember(initialCachedSchedule?.rawJson) {
        initialCachedSchedule?.rawJson?.let { runCatching { parseWeekInfo(it) }.getOrNull() }
    }

    var selectedDate by remember { mutableStateOf(today) }

    var date by remember { mutableStateOf(selectedDate.format(formatter)) }

    var errorText by remember { mutableStateOf<String?>(null) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var showCaptchaDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showAppInfoDialog by remember { mutableStateOf(false) }

    var currentGroupName by remember { mutableStateOf("") }
    var currentGroupId by remember { mutableStateOf(initialCachedSchedule?.groupId.orEmpty()) }
    var currentUserName by remember { mutableStateOf("") }
    var sessionGroup by remember { mutableStateOf<ScheduleGroup?>(null) }

    val faculties = remember { mutableStateListOf<ScheduleFaculty>() }
    val facultyGroups = remember { mutableStateListOf<ScheduleGroup>() }
    var selectedFaculty by remember { mutableStateOf<ScheduleFaculty?>(null) }
    var selectedDialogGroup by remember { mutableStateOf<ScheduleGroup?>(null) }
    var isLoadingFaculties by remember { mutableStateOf(false) }
    var isLoadingGroups by remember { mutableStateOf(false) }
    var groupDialogError by remember { mutableStateOf<String?>(null) }

    val savedCredentials = remember { CredentialsStorage.load(context) }
    var loginText by remember { mutableStateOf(savedCredentials?.login.orEmpty()) }
    var passwordText by remember { mutableStateOf(savedCredentials?.password.orEmpty()) }

    val lessons = remember {
        mutableStateListOf<SimpleLesson>().apply { addAll(initialCachedLessons) }
    }
    val weekDays = remember {
        mutableStateListOf<WeekDayHeader>().apply { addAll(initialCachedWeekDays) }
    }
    var weekInfo by remember { mutableStateOf(initialCachedWeekInfo) }

    fun applyRawJson(rawJson: String) {
        val parsedLessons = ScheduleParser.parseSimple(rawJson)
        val parsedWeekDays = parseWeekDays(rawJson)

        lessons.clear()
        lessons.addAll(parsedLessons)

        weekDays.clear()
        weekDays.addAll(parsedWeekDays)

        weekInfo = parseWeekInfo(rawJson)
    }

    fun applyCachedSchedule(cachedSchedule: CachedSchedule): Boolean {
        return runCatching {
            if (currentGroupId.isBlank()) {
                currentGroupId = cachedSchedule.groupId
            }
            applyRawJson(cachedSchedule.rawJson)
            ScheduleWidgetProvider.refreshAllWidgets(context)
        }.isSuccess
    }

    fun drawCachedScheduleFirst(preferredGroupId: String? = null): CachedSchedule? {
        val cachedSchedule = preferredGroupId
            ?.takeIf { it.isNotBlank() }
            ?.let { ScheduleCache.load(context = context, groupId = it) }
            ?: ScheduleCache.load(context = context)
            ?: return null

        return if (applyCachedSchedule(cachedSchedule)) cachedSchedule else null
    }

    fun cachedScheduleContainsDate(cachedSchedule: CachedSchedule, targetDate: LocalDate): Boolean {
        val targetDateText = targetDate.format(formatter)
        if (cachedSchedule.date == targetDateText) return true

        val parserFormatter = DateTimeFormatter.ofPattern("d-M-uuuu")
        return parseWeekDays(cachedSchedule.rawJson).any { header ->
            header.date == targetDateText || runCatching {
                LocalDate.parse(header.date.trim(), parserFormatter) == targetDate
            }.getOrDefault(false)
        }
    }

    fun isCachedScheduleActual(
        cachedSchedule: CachedSchedule?,
        targetDate: LocalDate,
        groupId: String? = null
    ): Boolean {
        if (cachedSchedule == null) return false
        if (!groupId.isNullOrBlank() && cachedSchedule.groupId != groupId) return false
        return cachedScheduleContainsDate(cachedSchedule, targetDate)
    }

    fun tryApplyCachedSchedule(targetDate: LocalDate, groupId: String? = null): Boolean {
        val cachedSchedule = ScheduleCache.load(context = context, groupId = groupId)
            ?: return false

        if (!isCachedScheduleActual(cachedSchedule, targetDate, groupId)) return false
        return applyCachedSchedule(cachedSchedule)
    }

    fun showNoInternetWarning() {
        errorText = NO_INTERNET_MESSAGE
        showErrorDialog = true
    }

    fun loadSchedule(
        targetDate: LocalDate = selectedDate,
        groupOverride: ScheduleGroup? = null,
        showOfflineWarning: Boolean = true,
        forceRefresh: Boolean = false
    ) {
        scope.launch {
            isLoading = true
            errorText = null
            showErrorDialog = false

            val requestDate = targetDate.format(formatter)
            date = requestDate

            val preferredGroupId = groupOverride?.id?.toString() ?: sessionGroup?.id?.toString()
            var renderedCache = drawCachedScheduleFirst(preferredGroupId)

            if (loginText.isBlank() || passwordText.isBlank()) {
                isLoading = false
                if (!isCachedScheduleActual(renderedCache, targetDate, renderedCache?.groupId)) {
                    showSettingsDialog = true
                }
                return@launch
            }

            if (!hasInternetConnection(context)) {
                isLoading = false
                if (showOfflineWarning) {
                    showNoInternetWarning()
                }
                return@launch
            }

            // After cache is rendered immediately, always enter the personal account
            // and read the actual user group before deciding whether parsing is needed.
            val authResult = withContext(Dispatchers.IO) {
                authApi.ensureAuthorized(
                    login = loginText.trim(),
                    password = passwordText.trim()
                )
            }

            val activeGroup = when (authResult) {
                is AuthResult.Error -> {
                    errorText = authResult.message
                    showErrorDialog = true
                    isLoading = false
                    return@launch
                }

                is AuthResult.Success -> authResult.activeGroup
            }

            val selectedSessionGroup = groupOverride ?: sessionGroup
            currentGroupId = selectedSessionGroup?.id?.toString() ?: activeGroup.groupId
            currentGroupName = selectedSessionGroup?.code ?: activeGroup.groupName
            currentUserName = activeGroup.userFullName

            val currentGroupCache = ScheduleCache.load(context = context, groupId = currentGroupId)
            if (currentGroupCache != null && currentGroupCache != renderedCache) {
                renderedCache = if (applyCachedSchedule(currentGroupCache)) currentGroupCache else renderedCache
            }

            if (!forceRefresh && isCachedScheduleActual(renderedCache, targetDate, currentGroupId)) {
                isLoading = false
                date = requestDate
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                scheduleApi.fetchSchedule(
                    date = requestDate,
                    groupId = currentGroupId
                )
            }

            when (result) {
                is ScheduleResult.Success -> {
                    ScheduleCache.save(
                        context = context,
                        rawJson = result.json,
                        date = requestDate,
                        groupId = currentGroupId
                    )
                    date = requestDate
                    applyRawJson(result.json)
                    ScheduleWidgetProvider.refreshAllWidgets(context)
                }

                is ScheduleResult.NeedCaptcha -> {
                    showCaptchaDialog = true
                }

                is ScheduleResult.Error -> {
                    errorText = result.message
                    showErrorDialog = true
                }
            }

            isLoading = false
        }
    }

    fun loadFacultyGroups(facultyId: Int) {
        scope.launch {
            isLoadingGroups = true
            groupDialogError = null
            facultyGroups.clear()

            if (!hasInternetConnection(context)) {
                groupDialogError = NO_INTERNET_MESSAGE
                isLoadingGroups = false
                return@launch
            }

            when (val result = withContext(Dispatchers.IO) { scheduleApi.fetchFacultyGroups(facultyId) }) {
                is FacultyGroupsResult.Success -> {
                    facultyGroups.addAll(result.groups)
                }

                is FacultyGroupsResult.NeedCaptcha -> {
                    groupDialogError = result.message
                    showCaptchaDialog = true
                }

                is FacultyGroupsResult.Error -> {
                    groupDialogError = result.message
                }
            }

            isLoadingGroups = false
        }
    }

    fun loadPerformance() {
        val login = loginText.trim()
        val password = passwordText.trim()
        if (login.isBlank() || password.isBlank()) {
            performanceError = "Не указан логин. Сохраните данные входа в настройках."
            return
        }
        if (performanceLoading) return

        scope.launch {
            performanceLoading = true
            performanceError = null

            if (!hasInternetConnection(context)) {
                performanceError = NO_INTERNET_MESSAGE
                performanceLoading = false
                return@launch
            }

            val authResult = withContext(Dispatchers.IO) {
                authApi.ensureAuthorized(login = login, password = password)
            }
            if (authResult is AuthResult.Error) {
                performanceError = authResult.message
                performanceLoading = false
                return@launch
            }

            val result = withContext(Dispatchers.IO) { performanceApi.fetchPerformance(login) }
            val planResult = withContext(Dispatchers.IO) { performanceApi.fetchPlan(login) }

            val reportTypes = if (planResult is PerformanceResult.Success) {
                runCatching { PerformanceParser.parsePlan(planResult.json) }.getOrDefault(emptyMap())
            } else {
                emptyMap()
            }

            when (result) {
                is PerformanceResult.Success -> {
                    val parsed = runCatching { PerformanceParser.parse(result.json, reportTypes) }.getOrNull()
                    if (parsed == null) {
                        performanceError = "Не удалось разобрать ответ."
                    } else {
                        performanceData = parsed
                    }
                }
                is PerformanceResult.Error -> performanceError = result.message
            }
            performanceLoading = false
        }
    }

    fun loadEdu() {
        val login = loginText.trim()
        val password = passwordText.trim()
        if (login.isBlank() || password.isBlank()) {
            eduError = "Не указан логин. Сохраните данные входа в настройках."
            return
        }
        if (eduLoading) return

        scope.launch {
            eduLoading = true
            eduError = null

            if (!hasInternetConnection(context)) {
                eduError = NO_INTERNET_MESSAGE
                eduLoading = false
                return@launch
            }

            suspend fun loadCoursesPage(): Pair<String, String>? {
                return when (val res = withContext(Dispatchers.IO) { eduApi.fetchCoursesHtml() }) {
                    is EduPageResult.Success -> res.html to res.finalUrl
                    is EduPageResult.Error -> {
                        eduError = res.message
                        null
                    }
                }
            }

            var coursesPage = loadCoursesPage() ?: run { eduLoading = false; return@launch }

            if (eduApi.isLoginPage(coursesPage.first, coursesPage.second)) {
                when (val loginResult = withContext(Dispatchers.IO) { eduApi.login(login, password) }) {
                    is EduLoginResult.Error -> {
                        eduError = loginResult.message
                        eduLoading = false
                        return@launch
                    }
                    EduLoginResult.Success -> { /* proceed */ }
                }

                coursesPage = loadCoursesPage() ?: run { eduLoading = false; return@launch }

                if (eduApi.isLoginPage(coursesPage.first, coursesPage.second)) {
                    eduError = "Не удалось войти в edu.gubkin.ru. Проверьте логин и пароль.\n" +
                        "Финальный URL: ${coursesPage.second}"
                    eduLoading = false
                    return@launch
                }
            }

            val coursesHtml = coursesPage.first

            var courseIds: List<Int> = EduParser.parseCourseIds(coursesHtml)
            var ajaxDiag: String? = null

            if (courseIds.isEmpty()) {
                val sesskey = eduApi.extractSesskey(coursesHtml)
                if (sesskey != null) {
                    when (val ajaxRes = withContext(Dispatchers.IO) {
                        eduApi.fetchEnrolledCoursesJson(sesskey)
                    }) {
                        is EduPageResult.Success -> {
                            courseIds = EduParser.parseCourseIdsFromAjax(ajaxRes.html)
                            if (courseIds.isEmpty()) {
                                ajaxDiag = "AJAX ответ (${ajaxRes.html.length} симв.): ${ajaxRes.html.take(300)}"
                            }
                        }
                        is EduPageResult.Error -> ajaxDiag = "AJAX: ${ajaxRes.message}"
                    }
                } else {
                    ajaxDiag = "sesskey не найден в HTML"
                }
            }

            if (courseIds.isEmpty()) {
                val hasCourseView = coursesHtml.contains("course/view.php", ignoreCase = true)
                val hasDataCourseId = coursesHtml.contains("data-course-id", ignoreCase = true)
                val hasCourseWord = coursesHtml.contains("course", ignoreCase = true)
                eduError = buildString {
                    append("Не найдено ни одного курса.\n")
                    append("URL: ${coursesPage.second}\n")
                    append("HTML: ${coursesHtml.length} симв.\n")
                    append("course/view.php в HTML: $hasCourseView\n")
                    append("data-course-id в HTML: $hasDataCourseId\n")
                    append("слово course в HTML: $hasCourseWord\n")
                    if (ajaxDiag != null) {
                        append("$ajaxDiag\n")
                    }
                }
                eduLoading = false
                return@launch
            }

            val rawSubjects = withContext(Dispatchers.IO) {
                courseIds.mapNotNull { id ->
                    when (val res = eduApi.fetchGradeHtml(id)) {
                        is EduPageResult.Success -> EduParser.parseSubject(res.html, fallbackName = "Курс #$id")
                        is EduPageResult.Error -> null
                    }
                }
            }

            // Fetch performance data if not already loaded to ensure proper filtering and naming
            val perfDataForFilter = performanceData ?: withContext(Dispatchers.IO) {
                val perfResult = performanceApi.fetchPerformance(login)
                val planResult = performanceApi.fetchPlan(login)
                val reportTypes = if (planResult is PerformanceResult.Success) {
                    runCatching { PerformanceParser.parsePlan(planResult.json) }.getOrDefault(emptyMap())
                } else emptyMap()
                
                if (perfResult is PerformanceResult.Success) {
                    runCatching { PerformanceParser.parse(perfResult.json, reportTypes) }.getOrNull()
                } else null
            }

            val subjects = if (perfDataForFilter != null) {
                rawSubjects.mapNotNull { eduSubject ->
                    val isEduKursovik = eduSubject.name.contains("курсовая", ignoreCase = true) || 
                                       eduSubject.name.contains("курсов", ignoreCase = true)
                    val isEduPractice = eduSubject.name.contains("практика", ignoreCase = true)

                    if (isEduKursovik || isEduPractice) {
                        // Course works and practices are not synced with LK. They keep their original Edu name.
                        val type = if (isEduKursovik) "курсовая работа" else "практика"
                        return@mapNotNull eduSubject.copy(reportType = type)
                    }

                    val matchedPerf = perfDataForFilter.subjects.find { perfSubj ->
                        val isPerfKursovik = perfSubj.name.contains("курсовая", ignoreCase = true) || 
                                           perfSubj.name.contains("курсов", ignoreCase = true)
                        val isPerfPractice = perfSubj.name.contains("практика", ignoreCase = true)
                        
                        // Ignore LK course works/practices when syncing regular subjects
                        if (isPerfKursovik || isPerfPractice) return@find false

                        val canonicalEdu = PerformanceParser.canonicalizeName(eduSubject.name)
                        val canonicalPerf = PerformanceParser.canonicalizeName(perfSubj.name)
                        
                        (canonicalPerf.isNotEmpty() && (canonicalEdu.contains(canonicalPerf) || canonicalPerf.contains(canonicalEdu))) || 
                        (perfSubj.name.isNotEmpty() && (eduSubject.name.contains(perfSubj.name, ignoreCase = true) || perfSubj.name.contains(eduSubject.name, ignoreCase = true)))
                    }
                    
                    if (matchedPerf != null) {
                        // For regular subjects, only include if matched
                        eduSubject.copy(name = matchedPerf.name, reportType = matchedPerf.reportType)
                    } else null
                }
            } else {
                // If performance data is still totally unavailable
                rawSubjects.mapNotNull { eduSubject ->
                    val isEduKursovik = eduSubject.name.contains("курсовая", ignoreCase = true) || 
                                       eduSubject.name.contains("курсов", ignoreCase = true)
                    val isEduPractice = eduSubject.name.contains("практика", ignoreCase = true)
                    if (isEduKursovik || isEduPractice) {
                        val type = if (isEduKursovik) "курсовая работа" else "практика"
                        eduSubject.copy(reportType = type)
                    } else {
                        eduSubject
                    }
                }
            }

            if (subjects.isEmpty()) {
                eduError = "Найдено ${courseIds.size} курс(ов), но не удалось разобрать ни одну страницу оценок."
            } else {
                eduData = PerformanceData(subjects = subjects, truancyAll = null, truancyJustified = null)
            }
            eduLoading = false
        }
    }

    fun loadFaculties() {
        scope.launch {
            if (loginText.isBlank() || passwordText.isBlank()) {
                showGroupDialog = false
                showSettingsDialog = true
                return@launch
            }

            isLoadingFaculties = true
            groupDialogError = null

            if (!hasInternetConnection(context)) {
                groupDialogError = NO_INTERNET_MESSAGE
                isLoadingFaculties = false
                return@launch
            }

            val authResult = withContext(Dispatchers.IO) {
                authApi.ensureAuthorized(
                    login = loginText.trim(),
                    password = passwordText.trim()
                )
            }

            when (authResult) {
                is AuthResult.Error -> {
                    groupDialogError = authResult.message
                    isLoadingFaculties = false
                    return@launch
                }

                is AuthResult.Success -> {
                    currentGroupId = sessionGroup?.id?.toString() ?: authResult.activeGroup.groupId
                    currentGroupName = sessionGroup?.code ?: authResult.activeGroup.groupName
                    currentUserName = authResult.activeGroup.userFullName
                }
            }

            when (val result = withContext(Dispatchers.IO) { scheduleApi.fetchFaculties() }) {
                is FacultyListResult.Success -> {
                    faculties.clear()
                    faculties.addAll(result.faculties)
                }

                is FacultyListResult.NeedCaptcha -> {
                    groupDialogError = result.message
                    showCaptchaDialog = true
                }

                is FacultyListResult.Error -> {
                    groupDialogError = result.message
                }
            }

            isLoadingFaculties = false
        }
    }

    fun openGroupDialog() {
        selectedDialogGroup = sessionGroup
        showGroupDialog = true
        if (faculties.isEmpty()) {
            loadFaculties()
        }
    }

    fun canOpenDate(targetDate: LocalDate): Boolean {
        return !targetDate.isBefore(today)
    }

    fun canSwitchWeek(weeks: Long): Boolean {
        return canOpenDate(selectedDate.plusWeeks(weeks))
    }

    fun switchWeek(weeks: Long) {
        val newDate = selectedDate.plusWeeks(weeks)
        if (!canOpenDate(newDate)) return

        selectedDate = newDate
        loadSchedule(newDate)
    }

    LaunchedEffect(selectedDate) {
        date = selectedDate.format(formatter)
    }

    LaunchedEffect(Unit) {
        if (savedCredentials != null) {
            loadSchedule(today, showOfflineWarning = false)
            loadPerformance()
            loadEdu()
        } else {
            val cachedSchedule = drawCachedScheduleFirst()
            if (!isCachedScheduleActual(cachedSchedule, today, cachedSchedule?.groupId)) {
                showSettingsDialog = true
            }
        }
    }

    DisposableEffect(context) {
        val prefs = context.getSharedPreferences("schedule_cache", Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            val preferredGroupId = sessionGroup?.id?.toString()
                ?: currentGroupId.takeIf { it.isNotBlank() }
            val cached = preferredGroupId
                ?.let { ScheduleCache.load(context = context, groupId = it) }
                ?: ScheduleCache.load(context = context)
            if (cached != null) {
                applyCachedSchedule(cached)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    if (showPerformanceScreen) {
        PerformanceScreen(
            data = performanceData,
            isLoading = performanceLoading,
            errorText = performanceError,
            source = "ЛК",
            onRefresh = { loadPerformance() },
            onBack = { showPerformanceScreen = false }
        )
        return
    }

    if (showEduScreen) {
        PerformanceScreen(
            data = eduData,
            isLoading = eduLoading,
            errorText = eduError,
            source = "Edu",
            onRefresh = { loadEdu() },
            onBack = { showEduScreen = false }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Расписание") },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Меню")
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        offset = DpOffset(x = (-8).dp, y = 8.dp),
                        modifier = Modifier
                            .background(Color.White)
                            .padding(vertical = 6.dp)
                    ) {
                        ModernMenuItem(
                            text = "Настройки входа",
                            icon = Icons.Default.Settings,
                            onClick = {
                                menuExpanded = false
                                showSettingsDialog = true
                            }
                        )
                        ModernMenuItem(
                            text = "Успеваемость",
                            icon = Icons.Default.Star,
                            onClick = {
                                menuExpanded = false
                                showPerformanceScreen = true
                            }
                        )
                        ModernMenuItem(
                            text = "EDU",
                            icon = Icons.Default.List,
                            onClick = {
                                menuExpanded = false
                                showEduScreen = true
                            }
                        )
                        ModernMenuItem(
                            text = "О приложении",
                            icon = Icons.Default.Info,
                            onClick = {
                                menuExpanded = false
                                showAppInfoDialog = true
                            }
                        )
                        ModernMenuItem(
                            text = "О нас",
                            icon = Icons.Default.Person,
                            onClick = {
                                menuExpanded = false
                                showAboutDialog = true
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { switchWeek(-1) },
                    enabled = !isLoading && canSwitchWeek(-1),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                        disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f)
                    )
                ) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Предыдущая неделя")
                }

                DatePickerField(
                    selectedDate = selectedDate,
                    minSelectableDate = today,
                    enabled = !isLoading,
                    onDateSelected = { newDate ->
                        if (canOpenDate(newDate)) {
                            selectedDate = newDate
                            loadSchedule(newDate)
                        }
                    }
                )

                IconButton(
                    onClick = { switchWeek(1) },
                    enabled = !isLoading,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                        disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f)
                    )
                ) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Следующая неделя")
                }

                IconButton(
                    onClick = {
                        selectedDate = today
                        loadSchedule(targetDate = today, forceRefresh = true)
                    },
                    enabled = !isLoading,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                        disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f)
                    )
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Обновить расписание")
                }

                Button(
                    onClick = { openGroupDialog() },
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = sessionGroup?.code ?: currentGroupName.ifBlank { "Группа" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            weekInfo?.let { info ->
                val parts = buildList {
                    if (info.number > 0) add("${info.number} неделя")
                    val typeName = info.typeName()
                    if (typeName.isNotBlank()) add("${typeName} неделя")
                }
                if (parts.isNotEmpty()) {
                    Text(
                        text = parts.joinToString(" • "),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            ScheduleTable(
                lessons = lessons,
                weekDays = weekDays,
                modifier = Modifier.weight(1f),
                today = today
            )
        }
    }

    if (showErrorDialog && !errorText.isNullOrBlank()) {
        ErrorDialog(
            message = errorText.orEmpty(),
            onDismiss = {
                showErrorDialog = false
                errorText = null
            }
        )
    }

    if (showSettingsDialog) {
        CredentialsDialog(
            login = loginText,
            password = passwordText,
            onLoginChange = { loginText = it },
            onPasswordChange = { passwordText = it },
            onDismiss = { showSettingsDialog = false },
            onSave = {
                CredentialsStorage.save(
                    context = context,
                    login = loginText.trim(),
                    password = passwordText.trim()
                )
                showSettingsDialog = false
                loadSchedule()
            }
        )
    }

    if (showGroupDialog) {
        GroupSelectionDialog(
            faculties = faculties,
            groups = facultyGroups,
            selectedFaculty = selectedFaculty,
            selectedGroup = selectedDialogGroup,
            isLoadingFaculties = isLoadingFaculties,
            isLoadingGroups = isLoadingGroups,
            errorText = groupDialogError,
            onFacultySelected = { faculty ->
                selectedFaculty = faculty
                selectedDialogGroup = null
                loadFacultyGroups(faculty.id)
            },
            onGroupSelected = { group ->
                selectedDialogGroup = group
            },
            onRefreshFaculties = { loadFaculties() },
            onDismiss = { showGroupDialog = false },
            onApply = {
                selectedDialogGroup?.let { group ->
                    sessionGroup = group
                    showGroupDialog = false
                    loadSchedule(selectedDate, groupOverride = group)
                }
            }
        )
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }

    if (showAppInfoDialog) {
        AppInfoDialog(onDismiss = { showAppInfoDialog = false })
    }

    if (showCaptchaDialog) {
        NativeCaptchaDialog(
            captchaApi = captchaApi,
            onDismiss = {
                showCaptchaDialog = false
            },
            onSuccess = {
                showCaptchaDialog = false
                if (showGroupDialog && faculties.isEmpty()) {
                    loadFaculties()
                } else if (showGroupDialog && selectedFaculty != null && facultyGroups.isEmpty()) {
                    loadFacultyGroups(selectedFaculty!!.id)
                } else {
                    loadSchedule()
                }
            }
        )
    }
}

@Composable
private fun ModernMenuItem(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .padding(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}
