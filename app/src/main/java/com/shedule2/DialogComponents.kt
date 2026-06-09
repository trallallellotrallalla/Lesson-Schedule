package com.shedule2

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
        title = {
            Text("Ошибка")
        },
        text = {
            Text(message)
        }
    )
}

@Composable
fun CredentialsDialog(
    login: String,
    password: String,
    onLoginChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = login.isNotBlank() && password.isNotBlank()
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
        title = {
            Text("Настройки входа")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Введите номер пропуска и пароль от ЛК.")
                OutlinedTextField(
                    value = login,
                    onValueChange = onLoginChange,
                    label = { Text("Номер пропуска") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("Пароль") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            PasswordVisibilityGlyph(
                                isVisible = passwordVisible,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                )
            }
        }
    )
}

@Composable
private fun PasswordVisibilityGlyph(
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val contentDescription = if (isVisible) "Скрыть пароль" else "Показать пароль"

    Canvas(
        modifier = modifier.semantics {
            this.contentDescription = contentDescription
        }
    ) {
        val strokeWidth = 1.8.dp.toPx()
        val eyeWidth = size.width * 0.9f
        val eyeHeight = size.height * 0.45f
        val topLeft = Offset(
            x = (size.width - eyeWidth) / 2f,
            y = (size.height - eyeHeight) / 2f
        )

        drawOval(
            color = color,
            topLeft = topLeft,
            size = Size(eyeWidth, eyeHeight),
            style = Stroke(width = strokeWidth)
        )
        drawCircle(
            color = color,
            radius = size.minDimension * 0.12f,
            center = center
        )

        if (!isVisible) {
            drawLine(
                color = color,
                start = Offset(size.width * 0.18f, size.height * 0.82f),
                end = Offset(size.width * 0.82f, size.height * 0.18f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun AppInfoDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
        title = {
            Text(
                text = "О приложении",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("Это полностью автономное приложение")
                        }
                        append(" без развернутого сервера.")
                    },
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                            append("Что это значит? ")
                        }
                        append("Это означает, что все действия происходят именно на ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("устройстве пользователя")
                        }
                        append(", а все данные сохраняются в ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("кэш устройства")
                        }
                        append(".")
                    },
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = buildAnnotatedString {
                        append("Ваши данные хранятся в ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("защиврованном формате кэш")
                        }
                        append(" и к нам ")
                        withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) {
                            append("НИКАК")
                        }
                        append(" не попадут :)")
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    )
}

@Composable
fun AboutDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
        title = {
            Text(
                text = "О нас",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            val aboutScrollState = rememberScrollState()
            val isAboutScrollNeeded by remember {
                derivedStateOf { aboutScrollState.maxValue > 0 }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(
                        state = aboutScrollState,
                        enabled = isAboutScrollNeeded
                    ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = buildAnnotatedString {
                        append("Привет, дорогой пользователь! ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("МЫ")
                        }
                        append(" — небольшая команда энтузиастов с факультета ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("АиВТ")
                        }
                        append(" группы ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("АА-23-08")
                        }
                        append(" — сделали версию ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("2.0")
                        }
                        append(" нашего приложения для просмотра расписания нашего университета. Нам очень важен твой отзыв об использовании ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("НАШЕГО")
                        }
                        append(" приложения. Любой фидбэк ты можешь оставить либо в отзывах RuStore, либо по контактам.")
                    },
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = "Наша команда:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append("Главный разработчик: ")
                            }
                            append("Леонтьев Глеб ")
                            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                append("ака Marsianin")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )

                    TelegramContactRow(username = "@Made_in_MARS")
                    VkContactRow(url = "https://vk.com/biker_on_moon")

                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append("Главный тестировщик: ")
                            }
                            append("Григорьев Вячеслав ")
                            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                append("ака ЫЫЫЫЫ")
                            }
                            append("\n")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append("Главный дизайнер: ")
                            }
                            append("Осадченко Светлана ")
                            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                append("ака Svetosa")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                            append("P.s.\n")
                        }
                        append("Планируется также версия для преподавателей, версия под IOS. Также в скором времени выложим проект на GitHub в открытый доступ")
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    )
}

@Composable
private fun TelegramContactRow(username: String) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "tg:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = username,
            modifier = Modifier.clickable {
                clipboardManager.setText(AnnotatedString(username))
                Toast.makeText(context, "Ник Telegram скопирован", Toast.LENGTH_SHORT).show()
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            textDecoration = TextDecoration.Underline
        )
    }
}

@Composable
private fun VkContactRow(url: String) {
    val uriHandler = LocalUriHandler.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "VK:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = url,
            modifier = Modifier.clickable {
                runCatching { uriHandler.openUri(url) }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            textDecoration = TextDecoration.Underline
        )
    }
}

@Composable
fun GroupSelectionDialog(
    faculties: List<ScheduleFaculty>,
    groups: List<ScheduleGroup>,
    selectedFaculty: ScheduleFaculty?,
    selectedGroup: ScheduleGroup?,
    isLoadingFaculties: Boolean,
    isLoadingGroups: Boolean,
    errorText: String?,
    onFacultySelected: (ScheduleFaculty) -> Unit,
    onGroupSelected: (ScheduleGroup) -> Unit,
    onRefreshFaculties: () -> Unit,
    onDismiss: () -> Unit,
    onApply: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Выбор группы")
        },
        confirmButton = {
            TextButton(
                onClick = onApply,
                enabled = selectedGroup != null && !isLoadingFaculties && !isLoadingGroups
            ) {
                Text("Выбрать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Выберите факультет, затем группу. Выбор действует только до закрытия приложения.")

                SelectorDropdown(
                    label = "Факультет",
                    value = selectedFaculty?.name.orEmpty(),
                    placeholder = if (isLoadingFaculties) "Загрузка факультетов..." else "Выберите факультет",
                    enabled = faculties.isNotEmpty() && !isLoadingFaculties,
                    items = faculties,
                    itemText = { it.name },
                    onSelected = onFacultySelected
                )

                if (isLoadingFaculties) {
                    LoadingRow("Загружаю факультеты...")
                }

                SelectorDropdown(
                    label = "Группа",
                    value = selectedGroup?.code.orEmpty(),
                    placeholder = when {
                        selectedFaculty == null -> "Сначала выберите факультет"
                        isLoadingGroups -> "Загрузка групп..."
                        else -> "Выберите группу"
                    },
                    enabled = selectedFaculty != null && groups.isNotEmpty() && !isLoadingGroups,
                    items = groups,
                    itemText = { it.code },
                    onSelected = onGroupSelected
                )

                if (isLoadingGroups) {
                    LoadingRow("Загружаю группы...")
                }

                if (!errorText.isNullOrBlank()) {
                    Text(
                        text = errorText,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = onRefreshFaculties) {
                        Text("Повторить загрузку факультетов")
                    }
                }
            }
        }
    )
}

@Composable
private fun LoadingRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp))
        Text(text)
    }
}

@Composable
private fun <T> SelectorDropdown(
    label: String,
    value: String,
    placeholder: String,
    enabled: Boolean,
    items: List<T>,
    itemText: (T) -> String,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = value.ifBlank { placeholder },
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 360.dp)
                    .heightIn(max = 360.dp)
            ) {
                items.forEach { item ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = itemText(item),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        onClick = {
                            expanded = false
                            onSelected(item)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun NativeCaptchaDialog(
    captchaApi: CaptchaApi,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var captchaCode by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var imageBytes by remember { mutableStateOf<ByteArray?>(null) }

    fun loadCaptcha() {
        scope.launch {
            isLoading = true
            errorText = null

            when (val result = withContext(Dispatchers.IO) { captchaApi.generateCaptchaImage() }) {
                is CaptchaImageResult.Success -> {
                    imageBytes = result.bytes
                }

                is CaptchaImageResult.Error -> {
                    errorText = result.message
                }
            }

            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadCaptcha()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        if (captchaCode.isBlank()) {
                            errorText = "Введите код с картинки."
                            return@launch
                        }

                        isLoading = true
                        errorText = null

                        when (val result = withContext(Dispatchers.IO) {
                            captchaApi.validateCaptcha(captchaCode.trim())
                        }) {
                            is CaptchaValidateResult.Success -> {
                                onSuccess()
                            }

                            is CaptchaValidateResult.Error -> {
                                errorText = result.message
                                captchaCode = ""
                                loadCaptcha()
                            }
                        }

                        isLoading = false
                    }
                },
                enabled = !isLoading
            ) {
                Text("Подтвердить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                imageBytes?.let { bytes ->
                    val bitmap = remember(bytes) {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Капча",
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.15f)
                        )
                    }
                }

                OutlinedTextField(
                    value = captchaCode,
                    onValueChange = { captchaCode = it },
                    label = { Text("Код капчи") },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = { loadCaptcha() },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isLoading) "Загрузка..." else "Обновить капчу")
                }

                errorText?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )
}
