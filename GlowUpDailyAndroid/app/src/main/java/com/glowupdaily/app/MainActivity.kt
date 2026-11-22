package com.glowupdaily.app

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GlowUpDailyApp() }
    }
}

private val AccentBlue = Color(0xFF7794FF)
private val AccentViolet = Color(0xFFAA8BFF)
private val AppBackground = Color(0xFF080D18)
private val AppSurface = Color(0xFF121A2A)
private val MutedText = Color(0xFF9AA8C0)

@Composable
private fun GlowUpTheme(dark: Boolean, content: @Composable () -> Unit) {
    val scheme = if (dark) darkColorScheme(
        primary = AccentBlue,
        secondary = AccentViolet,
        background = AppBackground,
        surface = AppSurface,
        onBackground = Color(0xFFF3F6FF),
        onSurface = Color(0xFFF3F6FF)
    ) else lightColorScheme(
        primary = Color(0xFF405CCB),
        secondary = Color(0xFF7754B9),
        background = Color(0xFFF4F6FC),
        surface = Color.White
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
private fun GlowUpDailyApp() {
    val context = LocalContext.current
    val store = remember { LocalStore(context) }
    var data by remember { mutableStateOf(store.load()) }
    var page by remember { mutableStateOf("Inicio") }
    var onboardingPhotoPath by remember { mutableStateOf("") }
    var mealPhotoPath by remember { mutableStateOf("") }
    var pendingPhotoTarget by remember { mutableStateOf("care") }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    var faceAiResult by remember { mutableStateOf("") }
    var foodAiResult by remember { mutableStateOf("") }
    var faceAiLoading by remember { mutableStateOf(false) }
    var foodAiLoading by remember { mutableStateOf(false) }
    var faceAiError by remember { mutableStateOf("") }
    var foodAiError by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun saveData(next: AppData) {
        data = next
        store.save(next)
    }
    fun updateData(transform: (AppData) -> AppData) = saveData(transform(data))
    fun receivePhoto(path: String, target: String) {
        when (target) {
            "profile" -> {
                onboardingPhotoPath = path
                data.profile?.let { saveData(data.copy(profile = it.copy(profilePhotoPath = path))) }
            }
            "care" -> updateData { it.copy(skinPhotoPaths = (it.skinPhotoPaths + path).takeLast(12)) }
            "meal" -> mealPhotoPath = path
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = cameraFile
        if (ok && file != null && file.exists()) {
            runCatching { normalizeJpeg(file) }
            receivePhoto(file.absolutePath, pendingPhotoTarget)
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val folder = File(context.filesDir, "images").apply { mkdirs() }
                val outFile = File(folder, "image_${System.currentTimeMillis()}.jpg")
                importAsJpeg(context, uri, outFile)
                receivePhoto(outFile.absolutePath, pendingPhotoTarget)
            }
        }
    }
    fun choosePhoto(target: String, camera: Boolean) {
        pendingPhotoTarget = target
        if (camera) {
            runCatching {
                val folder = File(context.filesDir, "images").apply { mkdirs() }
                val file = File(folder, "camera_${System.currentTimeMillis()}.jpg")
                cameraFile = file
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                cameraLauncher.launch(uri)
            }
        } else {
            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
    fun completeTask(id: String, points: Int) {
        val key = "${LocalDate.now()}|$id"
        if (key !in data.completions) {
            val nextCompletions = data.completions + key
            var nextXp = data.xp + points
            val todayCareIds = listOf("face_am_clean", "face_am_moist", "face_am_spf", "face_pm_clean", "face_pm_moist", "face_pm_check")
            val todayDone = todayCareIds.all { "${LocalDate.now()}|$it" in nextCompletions }
            val bonusKey = "${LocalDate.now()}|daily_bonus"
            if (todayDone && bonusKey !in nextCompletions) {
                nextCompletions.plus(bonusKey)
                nextXp += 10
            }
            saveData(data.copy(completions = nextCompletions + if (todayDone) setOf(bonusKey) else emptySet(), xp = nextXp))
        }
    }
    fun analyzePhoto(path: String, kind: String) {
        if (kind == "face") {
            faceAiLoading = true; faceAiError = ""; faceAiResult = ""
        } else {
            foodAiLoading = true; foodAiError = ""; foodAiResult = ""
        }
        scope.launch {
            try {
                val result = AiService.analyze(path, kind)
                if (kind == "face") faceAiResult = result else foodAiResult = result
            } catch (e: Exception) {
                val message = e.message ?: "No se pudo conectar con el servicio de IA."
                if (kind == "face") faceAiError = message else foodAiError = message
            } finally {
                if (kind == "face") faceAiLoading = false else foodAiLoading = false
            }
        }
    }

    LaunchedEffect(data.reminders) { ReminderScheduler.scheduleAll(context, data.reminders) }
    val isDark = data.profile?.darkTheme ?: true

    GlowUpTheme(isDark) {
        if (data.profile == null) {
            OnboardingScreen(
                photoPath = onboardingPhotoPath,
                onTakePhoto = { choosePhoto("profile", true) },
                onChoosePhoto = { choosePhoto("profile", false) },
                onFinish = { profile ->
                    saveData(data.copy(profile = profile.copy(profilePhotoPath = onboardingPhotoPath)))
                    page = "Inicio"
                }
            )
        } else {
            val nav = listOf(
                Triple("Inicio", "Inicio", Icons.Filled.Home),
                Triple("Care", "Care", Icons.Filled.Face),
                Triple("Training", "Training", Icons.Filled.FitnessCenter),
                Triple("Nutrition", "Nutri", Icons.Filled.Restaurant),
                Triple("Más", "Más", Icons.Filled.MoreHoriz)
            )
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        nav.forEach { item ->
                            NavigationBarItem(
                                selected = if (item.first == "Más") page in listOf("Más", "Evolution", "Levels", "Calendar", "Profile") else page == item.first,
                                onClick = { page = item.first },
                                icon = { androidx.compose.material3.Icon(item.third, contentDescription = item.second) },
                                label = { Text(item.second, fontSize = 10.sp) }
                            )
                        }
                    }
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when (page) {
                        "Inicio" -> DashboardScreen(data, ::completeTask, { page = it }, { page = "Training" })
                        "Care" -> CareScreen(
                            data = data,
                            onComplete = ::completeTask,
                            onTakePhoto = { choosePhoto("care", true) },
                            onChoosePhoto = { choosePhoto("care", false) },
                            onAnalyze = { path -> analyzePhoto(path, "face") },
                            aiResult = faceAiResult,
                            aiError = faceAiError,
                            aiLoading = faceAiLoading,
                            onNotesChange = { notes -> updateData { it.copy(careNotes = notes) } },
                            onHairNotesChange = { notes -> updateData { it.copy(hairNotes = notes) } },
                            onDeletePhoto = { path -> updateData { it.copy(skinPhotoPaths = it.skinPhotoPaths - path) }; File(path).delete() }
                        )
                        "Training" -> TrainingScreen(
                            profile = data.profile,
                            completed = LocalDate.now().toString() in data.trainingSessions,
                            onFinish = {
                                val today = LocalDate.now().toString()
                                if (today !in data.trainingSessions) saveData(data.copy(trainingSessions = data.trainingSessions + today, completions = data.completions + "$today|training", xp = data.xp + 25))
                            }
                        )
                        "Nutrition" -> NutritionScreen(
                            data = data,
                            photoPath = mealPhotoPath,
                            aiResult = foodAiResult,
                            aiError = foodAiError,
                            aiLoading = foodAiLoading,
                            onTakePhoto = { choosePhoto("meal", true) },
                            onChoosePhoto = { choosePhoto("meal", false) },
                            onAnalyze = { path -> analyzePhoto(path, "food") },
                            onClearPhoto = { mealPhotoPath = "" },
                            onAddMeal = { meal -> saveData(data.copy(meals = data.meals + meal, completions = data.completions + "${meal.date}|meal:${meal.id}", xp = data.xp + 5)); mealPhotoPath = "" }
                        )
                        "Evolution" -> EvolutionScreen(data, onBack = { page = "Más" })
                        "Levels" -> LevelsScreen(data, onBack = { page = "Más" })
                        "Calendar" -> CalendarScreen(data, onBack = { page = "Más" })
                        "Profile" -> ProfileScreen(
                            data = data,
                            onBack = { page = "Más" },
                            onProfileChange = { profile -> saveData(data.copy(profile = profile)) },
                            onReminderChange = { setting ->
                                val next = data.reminders.map { if (it.id == setting.id) setting else it }
                                saveData(data.copy(reminders = next))
                            },
                            onExport = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_SUBJECT, "Copia de seguridad Glow Up Daily")
                                    putExtra(Intent.EXTRA_TEXT, store.exportJson(data))
                                }
                                context.startActivity(Intent.createChooser(send, "Exportar mis datos"))
                            },
                            onDeleteAll = {
                                File(context.filesDir, "images").deleteRecursively()
                                store.clear()
                                data = AppData()
                                page = "Inicio"
                            }
                        )
                        else -> MoreScreen(onNavigate = { page = it }, data = data)
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingScreen(
    photoPath: String,
    onTakePhoto: () -> Unit,
    onChoosePhoto: () -> Unit,
    onFinish: (UserProfile) -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("16") }
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var activity by remember { mutableStateOf("Ligera") }
    var dailyMinutes by remember { mutableStateOf("30") }
    var goals by remember { mutableStateOf(setOf<String>()) }
    var place by remember { mutableStateOf("Casa") }
    var equipment by remember { mutableStateOf("Sin equipamiento") }
    var days by remember { mutableStateOf("3") }
    var session by remember { mutableStateOf("30") }
    var experience by remember { mutableStateOf("Principiante") }
    var foodNotes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val goalOptions = listOf("Cuidado personal", "Fuerza", "Coordinación", "Movilidad y flexibilidad", "Resistencia", "Cuidado de la piel", "Rutina capilar", "Dormir mejor", "Constancia y disciplina", "Alimentación variada")

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 22.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(AccentBlue), contentAlignment = Alignment.Center) {
                Text("G", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("GLOW UP DAILY", fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("Tu espacio privado de progreso", color = MutedText, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(26.dp))
        if (step == 0) {
            Text("Bienvenido a", color = MutedText, fontSize = 18.sp)
            Text("Glow Up Daily", fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Construye hábitos, cuida de ti y descubre tu progreso, un día a la vez.", color = MutedText, fontSize = 16.sp)
        } else {
            Text("CONFIGURACIÓN INICIAL · ${step}/4", color = AccentBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            LinearProgressIndicator(progress = { step / 4f }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(5.dp).clip(CircleShape))
            Spacer(Modifier.height(18.dp))
            Text(
                listOf("", "Tus objetivos", "Tu entrenamiento", "Preferencias", "Tu fotografía")[step],
                fontSize = 27.sp, fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(18.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (step) {
                0 -> {
                    Text("Antes de empezar, configuraremos tu espacio personal.", color = MutedText)
                    Spacer(Modifier.height(18.dp))
                    Field("¿Cómo quieres que te llamemos?", name, { name = it }, "Tu nombre")
                    Field("¿Qué edad tienes?", age, { age = it }, "Edad", KeyboardType.Number)
                    Field("Estatura en centímetros", height, { height = it }, "Ej. 165", KeyboardType.Number)
                    Field("Peso en kilogramos (opcional)", weight, { weight = it }, "Opcional", KeyboardType.Decimal)
                    Text("Nivel de actividad actual", color = MutedText, fontSize = 13.sp)
                    ChipOptions(listOf("Baja", "Ligera", "Moderada", "Activa"), activity) { activity = it }
                    Field("Tiempo que puedes dedicar al día (minutos)", dailyMinutes, { dailyMinutes = it }, "30", KeyboardType.Number)
                }
                1 -> {
                    Text("Elige lo que te gustaría trabajar. Puedes cambiarlo más adelante.", color = MutedText)
                    goalOptions.forEach { goal ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                            goals = if (goal in goals) goals - goal else goals + goal
                        }.padding(vertical = 5.dp)) {
                            Checkbox(checked = goal in goals, onCheckedChange = { checked -> goals = if (checked) goals + goal else goals - goal })
                            Text(goal, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
                2 -> {
                    Text("Usaremos tus respuestas para sugerir sesiones razonables y progresivas.", color = MutedText)
                    Text("¿Dónde entrenas?", color = MutedText, fontSize = 13.sp)
                    ChipOptions(listOf("Casa", "Gimnasio", "Ambos"), place) { place = it }
                    Text("¿Qué equipamiento tienes?", color = MutedText, fontSize = 13.sp)
                    ChipOptions(listOf("Sin equipamiento", "Bandas", "Mancuernas", "Variado"), equipment) { equipment = it }
                    Field("Días disponibles por semana", days, { days = it }, "3", KeyboardType.Number)
                    Field("Minutos por sesión", session, { session = it }, "30", KeyboardType.Number)
                    Text("Experiencia entrenando", color = MutedText, fontSize = 13.sp)
                    ChipOptions(listOf("Principiante", "Intermedio", "Avanzado"), experience) { experience = it }
                }
                3 -> {
                    Text("Esta sección es opcional. Puedes dejarla vacía.", color = MutedText)
                    Field("Alergias o alimentos que prefieres evitar", foodNotes, { foodNotes = it }, "Escribe aquí (opcional)")
                    Text("La aplicación no crea dietas restrictivas ni objetivos de pérdida de peso.", color = MutedText, fontSize = 12.sp)
                }
                4 -> {
                    Text("Puedes añadir una foto ahora o hacerlo luego desde Face & Care.", color = MutedText)
                    if (photoPath.isNotBlank()) PrivatePhoto(photoPath, Modifier.fillMaxWidth().height(190.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onTakePhoto, modifier = Modifier.weight(1f)) { Text("Abrir cámara") }
                        OutlinedButton(onClick = onChoosePhoto, modifier = Modifier.weight(1f)) { Text("Elegir foto") }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("La foto se guarda en el espacio privado de esta app. No se analiza ni se envía sin que pulses el botón de análisis y configures un servidor.", color = MutedText, fontSize = 12.sp)
                }
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) OutlinedButton(onClick = { step--; error = "" }, modifier = Modifier.weight(0.8f)) { Text("Atrás") }
            Button(
                onClick = {
                    error = ""
                    if (step == 0) {
                        if (name.isBlank() || (age.toIntOrNull() ?: 0) !in 10..100 || (height.toIntOrNull() ?: 0) !in 80..240 || (dailyMinutes.toIntOrNull() ?: 0) !in 5..240) {
                            error = "Revisa tu nombre, edad, estatura y tiempo diario."
                        } else step++
                    } else if (step < 4) step++ else {
                        val parsedAge = age.toIntOrNull() ?: 16
                        val profile = UserProfile(
                            name = name.trim(), age = parsedAge, heightCm = height.toIntOrNull() ?: 0,
                            weightKg = weight.replace(',', '.').toDoubleOrNull()?.takeIf { it in 20.0..350.0 },
                            activityLevel = activity, dailyMinutes = dailyMinutes.toIntOrNull() ?: 30,
                            goals = goals.toList(), trainingPlace = place, equipment = equipment,
                            daysPerWeek = (days.toIntOrNull() ?: 3).coerceIn(1, 7),
                            sessionMinutes = (session.toIntOrNull() ?: 30).coerceIn(10, 120),
                            experience = experience, foodNotes = foodNotes.trim()
                        )
                        onFinish(profile)
                    }
                }, modifier = Modifier.weight(1.2f)
            ) { Text(if (step == 4) "Crear mi espacio" else if (step == 0) "Comenzar" else "Continuar") }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onValue: (String) -> Unit, placeholder: String = "", keyboard: KeyboardType = KeyboardType.Text) {
    Column(Modifier.fillMaxWidth().padding(bottom = 11.dp)) {
        Text(label, fontSize = 13.sp, color = MutedText)
        OutlinedTextField(
            value = value, onValueChange = onValue, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = MutedText) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard)
        )
    }
}

@Composable
private fun ChipOptions(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        options.forEach { option -> FilterChip(selected = selected == option, onClick = { onSelect(option) }, label = { Text(option) }) }
    }
}

@Composable
private fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), content = content
    )
}

@Composable
private fun ScreenHeading(eyebrow: String, title: String, subtitle: String = "") {
    Column {
        Text(eyebrow.uppercase(), color = AccentBlue, fontSize = 10.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold)
        Text(title, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        if (subtitle.isNotBlank()) Text(subtitle, color = MutedText, fontSize = 13.sp)
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)
    ) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content) }
}

@Composable
private fun MetricCard(label: String, value: String, detail: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = MutedText, fontSize = 11.sp)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 23.sp)
            Text(detail, color = MutedText, fontSize = 10.sp)
        }
    }
}

@Composable
private fun DashboardScreen(data: AppData, onComplete: (String, Int) -> Unit, onNavigate: (String) -> Unit, onTraining: () -> Unit) {
    val profile = data.profile ?: return
    val tasks = listOf(
        Triple("face_am_clean", "Limpieza suave · mañana", 3),
        Triple("face_am_moist", "Hidratación · mañana", 3),
        Triple("face_am_spf", "Protección solar", 4),
        Triple("face_pm_clean", "Limpieza suave · noche", 3),
        Triple("face_pm_moist", "Hidratación · noche", 3),
        Triple("face_pm_check", "Revisión de hábitos · noche", 2)
    )
    val done = tasks.count { "${LocalDate.now()}|${it.first}" in data.completions }
    val level = ProgressRules.levelFor(data.xp)
    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("es")))
    ScreenColumn {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("GLOW UP DAILY", color = AccentBlue, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.8.sp)
                Text("Hola, ${profile.name}", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(today.replaceFirstChar { it.uppercase() }, color = MutedText, fontSize = 12.sp)
            }
            if (profile.profilePhotoPath.isNotBlank()) PrivatePhoto(profile.profilePhotoPath, Modifier.size(54.dp).clip(CircleShape))
            else Box(Modifier.size(50.dp).clip(CircleShape).background(AccentBlue.copy(alpha = .18f)), contentAlignment = Alignment.Center) { Text(profile.name.take(1).uppercase(), color = AccentBlue, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        }
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("MI PROGRESO DE HOY", color = AccentBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text("$done de ${tasks.size} hábitos", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(if (done == tasks.size) "Rutina de cuidado completada" else "Un paso a la vez. Mantén tu ritmo.", color = MutedText, fontSize = 12.sp)
                }
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(76.dp)) {
                    CircularProgressIndicator(progress = done / tasks.size.toFloat(), modifier = Modifier.fillMaxSize(), strokeWidth = 7.dp, color = AccentBlue, trackColor = AccentBlue.copy(alpha = .15f))
                    Text("${done * 100 / tasks.size}%", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            LinearProgressIndicator(progress = done / tasks.size.toFloat(), modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape))
            Button(onClick = { onNavigate("Care") }, modifier = Modifier.fillMaxWidth()) { Text("Continuar mi rutina") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MetricCard("NIVEL ${level + 1}", ProgressRules.levelNames[level], "${data.xp} XP acumulados", Modifier.weight(1f))
            MetricCard("RACHA", "${ProgressRules.currentStreak(data.completions)} días", "Constancia reciente", Modifier.weight(1f))
        }
        Panel {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Hábitos de hoy", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("+XP", color = MutedText, fontSize = 11.sp)
            }
            tasks.forEach { (id, label, points) ->
                val checked = "${LocalDate.now()}|$id" in data.completions
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = checked, enabled = !checked, onCheckedChange = { if (it) onComplete(id, points) })
                    Text(label, modifier = Modifier.weight(1f), fontSize = 13.sp, color = if (checked) MutedText else MaterialTheme.colorScheme.onSurface)
                    Text(if (checked) "Listo" else "+$points", color = if (checked) AccentBlue else MutedText, fontSize = 11.sp)
                }
            }
        }
        Panel {
            Text("Tu siguiente actividad", fontWeight = FontWeight.Bold)
            Text("Sesión de movimiento adaptada a tu disponibilidad: ${profile.sessionMinutes} minutos, ${profile.daysPerWeek} días por semana.", color = MutedText, fontSize = 12.sp)
            Button(onClick = onTraining, modifier = Modifier.fillMaxWidth()) { Text("Ir a Training") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            QuickAction("Face & Care", "Rutinas y fotos", Modifier.weight(1f)) { onNavigate("Care") }
            QuickAction("Nutrition AI", "Registro manual", Modifier.weight(1f)) { onNavigate("Nutrition") }
        }
    }
}

@Composable
private
