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
private fun QuickAction(title: String, desc: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp)) { Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp); Text(desc, color = MutedText, fontSize = 11.sp) }
    }
}

@Composable
private fun CareScreen(
    data: AppData,
    onComplete: (String, Int) -> Unit,
    onTakePhoto: () -> Unit,
    onChoosePhoto: () -> Unit,
    onAnalyze: (String) -> Unit,
    aiResult: String,
    aiError: String,
    aiLoading: Boolean,
    onNotesChange: (String) -> Unit,
    onHairNotesChange: (String) -> Unit,
    onDeletePhoto: (String) -> Unit
) {
    var consentToSend by remember { mutableStateOf(false) }
    val morning = listOf(Triple("face_am_clean", "Limpieza suave", "Usa un limpiador suave o agua según lo que tolere tu piel."), Triple("face_am_moist", "Hidratación", "Aplica una hidratante sencilla si tu piel la necesita."), Triple("face_am_spf", "Protección solar", "Usa protección solar adecuada cuando vayas a estar al aire libre."))
    val night = listOf(Triple("face_pm_clean", "Limpieza suave", "Retira la suciedad del día sin frotar con fuerza."), Triple("face_pm_moist", "Hidratación", "Mantén una rutina cómoda y sencilla."), Triple("face_pm_check", "Seguimiento de hábitos", "Registra cómo te fue sin juzgar tu apariencia."))
    ScreenColumn {
        ScreenHeading("FACE & CARE", "Cuidado personal", "Hábitos sencillos para cuidar de tu piel y cabello.")
        Panel {
            Text("Rutina de mañana", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            morning.forEach { (id, title, desc) -> CareTask(id, title, desc, data, onComplete) }
        }
        Panel {
            Text("Rutina de noche", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            night.forEach { (id, title, desc) -> CareTask(id, title, desc, data, onComplete) }
        }
        Panel {
            Text("Fotografías privadas", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Añade fotos para tu propio seguimiento. No se hacen puntuaciones de belleza ni comparaciones.", color = MutedText, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onTakePhoto, modifier = Modifier.weight(1f)) { Text("Cámara") }
                OutlinedButton(onClick = onChoosePhoto, modifier = Modifier.weight(1f)) { Text("Galería") }
            }
            data.skinPhotoPaths.lastOrNull()?.let { last ->
                PrivatePhoto(last, Modifier.fillMaxWidth().height(180.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = consentToSend, onCheckedChange = { consentToSend = it })
                    Text("Acepto enviar esta foto al servidor de IA configurado para recibir orientación general.", color = MutedText, fontSize = 11.sp)
                }
                Button(onClick = { onAnalyze(last) }, modifier = Modifier.fillMaxWidth(), enabled = !aiLoading && consentToSend) { Text(if (aiLoading) "Analizando…" else "Solicitar orientación con IA") }
            }
            if (data.skinPhotoPaths.isEmpty()) Text("Todavía no has añadido fotos.", color = MutedText, fontSize = 12.sp)
            if (data.skinPhotoPaths.isNotEmpty()) {
                Text("Fotos guardadas: ${data.skinPhotoPaths.size}", color = MutedText, fontSize = 11.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    data.skinPhotoPaths.forEach { path ->
                        Box {
                            PrivatePhoto(path, Modifier.size(74.dp).clip(RoundedCornerShape(12.dp)))
                            Text("×", color = Color.White, modifier = Modifier.align(Alignment.TopEnd).clip(CircleShape).background(Color.Black.copy(alpha = .7f)).clickable { onDeletePhoto(path) }.padding(horizontal = 7.dp))
                        }
                    }
                }
            }
            AiResponse(aiLoading, aiError, aiResult)
        }
        Panel {
            Text("Notas sobre mi piel", fontWeight = FontWeight.Bold)
            OutlinedTextField(value = data.careNotes, onValueChange = onNotesChange, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Anota productos o hábitos que te funcionen…") }, minLines = 2)
        }
        Panel {
            Text("Rutina capilar", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Tipo de cabello: ${data.profile?.hairType ?: "No indicado"}", color = MutedText, fontSize = 12.sp)
            Text("Adapta el cuidado y el peinado a tus preferencias. No hay un estilo obligatorio.", color = MutedText, fontSize = 12.sp)
            OutlinedTextField(value = data.hairNotes, onValueChange = onHairNotesChange, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Tipo de cabello, peinados que te gustan, frecuencia de lavado…") }, minLines = 3)
        }
        Panel {
            Text("Nota importante", fontWeight = FontWeight.Bold)
            Text("La app no diagnostica problemas de piel a partir de fotos. Si una molestia persiste, consulta a un profesional de salud.", color = MutedText, fontSize = 12.sp)
        }
    }
}

@Composable
private fun CareTask(id: String, title: String, desc: String, data: AppData, onComplete: (String, Int) -> Unit) {
    val done = "${LocalDate.now()}|$id" in data.completions
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(desc, color = MutedText, fontSize = 11.sp)
        }
        if (done) Text("Hecho", color = AccentBlue, fontSize = 11.sp)
        else TextButton(onClick = { onComplete(id, 3) }) { Text("Completar") }
    }
}

@Composable
private fun AiResponse(loading: Boolean, error: String, result: String) {
    if (loading) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text(" Contactando el servicio…", color = MutedText, fontSize = 12.sp) }
    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    if (result.isNotBlank()) {
        Divider(color = MutedText.copy(alpha = .2f))
        Text("Orientación recibida", fontWeight = FontWeight.Bold)
        Text(result, color = MutedText, fontSize = 12.sp)
    }
}

@Composable
private fun TrainingScreen(profile: UserProfile?, completed: Boolean, onFinish: () -> Unit) {
    var category by remember { mutableStateOf("Fuerza") }
    var seconds by remember { mutableIntStateOf(30) }
    var running by remember { mutableStateOf(false) }
    LaunchedEffect(running) {
        while (running && seconds > 0) {
            delay(1000)
            seconds -= 1
        }
        if (seconds == 0) running = false
    }
    val categories = listOf("Fuerza", "Resistencia", "Coordinación", "Velocidad", "Movilidad", "Flexibilidad", "Estabilidad", "Recuperación")
    val exercises = when (category) {
        "Fuerza" -> listOf("Sentadilla a una silla" to "2 series de 8–10 repeticiones. Mantén un movimiento cómodo y controlado.", "Flexiones en pared" to "2 series de 8 repeticiones. Ajusta la distancia para que sea cómodo.", "Puente de glúteos" to "2 series de 8–10 repeticiones, sin forzar la espalda.")
        "Resistencia" -> listOf("Marcha en el sitio" to "2 bloques de 60 segundos a un ritmo que permita hablar.", "Paso lateral" to "2 bloques de 30 segundos con control.")
        "Coordinación" -> listOf("Toques alternos de rodilla" to "2 bloques de 30 segundos, alternando lados.", "Equilibrio con apoyo" to "2 bloques de 20 segundos por lado, cerca de una pared.")
        "Velocidad" -> listOf("Pasos rápidos bajos" to "2 bloques de 10 segundos, con pasos cortos y controlados.", "Toques alternos al frente" to "2 bloques de 15 segundos sin saltos ni movimientos bruscos.")
        "Movilidad" -> listOf("Círculos de hombros" to "8 repeticiones lentas hacia cada dirección.", "Movilidad de tobillos" to "8 repeticiones suaves por lado.")
        "Flexibilidad" -> listOf("Estiramiento de pantorrilla" to "Mantén 15–20 segundos por lado, sin rebotes.", "Estiramiento suave de pecho" to "Mantén 15–20 segundos sin dolor.")
        "Estabilidad" -> listOf("Bird dog" to "2 series de 6 repeticiones alternas, con movimiento lento.", "Equilibrio con apoyo" to "2 bloques de 15 segundos por lado, cerca de una pared.")
        else -> listOf("Respiración tranquila" to "Un minuto de respiración natural.", "Paseo suave" to "Camina a un ritmo cómodo durante 3–5 minutos.")
    }
    ScreenColumn {
        ScreenHeading("TRAINING", "Entrenamiento", "Progresivo, adaptable y con tiempo para recuperarte.")
        Panel {
            Text("Tu planificación", fontWeight = FontWeight.Bold)
            Text("${profile?.trainingPlace ?: "Casa"} · ${profile?.daysPerWeek ?: 3} días por semana · ${profile?.sessionMinutes ?: 30} min por sesión", color = MutedText, fontSize = 12.sp)
            Text("Distribuye las sesiones durante la semana y deja espacio para recuperar entre días de trabajo.", color = MutedText, fontSize = 11.sp)
            Text("Experiencia: ${profile?.experience ?: "Principiante"} · Equipo: ${profile?.equipment ?: "Sin equipamiento"}", color = MutedText, fontSize = 12.sp)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            categories.forEach { FilterChip(selected = category == it, onClick = { category = it }, label = { Text(it) }) }
        }
        Panel {
            Text("Sesión de $category", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            exercises.forEachIndexed { index, exercise ->
                Text("${index + 1}. ${exercise.first}", fontWeight = FontWeight.SemiBold)
                Text(exercise.second, color = MutedText, fontSize = 12.sp)
                if (index != exercises.lastIndex) Divider(color = MutedText.copy(alpha = .15f))
            }
            Text("Descanso", fontWeight = FontWeight.Bold)
            Text("Descansa entre ejercicios y adapta las repeticiones a cómo te sientes.", color = MutedText, fontSize = 12.sp)
        }
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(Icons.Filled.Timer, contentDescription = null, tint = AccentBlue)
                Text("Temporizador", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(start = 7.dp))
            }
            Text(String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60), fontSize = 42.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { running = false; seconds = 30 }, modifier = Modifier.weight(1f)) { Text("30 s") }
                OutlinedButton(onClick = { running = false; seconds = 60 }, modifier = Modifier.weight(1f)) { Text("1 min") }
                OutlinedButton(onClick = { running = false; seconds = 90 }, modifier = Modifier.weight(1f)) { Text("90 s") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { running = !running }, modifier = Modifier.weight(1f)) { Text(if (running) "Pausar" else "Iniciar") }
                OutlinedButton(onClick = { running = false; seconds = 30 }, modifier = Modifier.weight(1f)) { Text("Reiniciar") }
            }
        }
        Panel {
            Text("Registro de sesión", fontWeight = FontWeight.Bold)
            Text(if (completed) "Ya registraste tu sesión de hoy. ¡Buen trabajo!" else "Cuando termines una sesión adecuada para ti, registra tu actividad.", color = MutedText, fontSize = 12.sp)
            Button(onClick = onFinish, enabled = !completed, modifier = Modifier.fillMaxWidth()) { Text(if (completed) "Sesión registrada hoy" else "Completar sesión · +25 XP") }
        }
        Text("Seguridad: calienta suavemente, toma agua y descansa. Detente si aparece dolor, mareo o malestar. No necesitas entrenar al máximo.", color = MutedText, fontSize = 11.sp)
    }
}

@Composable
private fun NutritionScreen(
    data: AppData,
    photoPath: String,
    aiResult: String,
    aiError: String,
    aiLoading: Boolean,
    onTakePhoto: () -> Unit,
    onChoosePhoto: () -> Unit,
    onAnalyze: (String) -> Unit,
    onClearPhoto: () -> Unit,
    onAddMeal: (MealEntry) -> Unit
) {
    var consentToSend by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var portion by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var sugar by remember { mutableStateOf("") }
    var fiber by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val todayMeals = data.meals.filter { it.date == LocalDate.now().toString() }
    ScreenColumn {
        ScreenHeading("NUTRITION AI", "Alimentación", "Registra tus comidas y aprende sobre variedad nutricional.")
        Panel {
            Text("Fotografía de la comida", fontWeight = FontWeight.Bold)
            Text("La foto por sí sola no confirma ingredientes ni cantidades. Cualquier análisis sería aproximado.", color = MutedText, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onTakePhoto, modifier = Modifier.weight(1f)) { Text("Cámara") }
                OutlinedButton(onClick = onChoosePhoto, modifier = Modifier.weight(1f)) { Text("Galería") }
            }
            if (photoPath.isNotBlank()) {
                PrivatePhoto(photoPath, Modifier.fillMaxWidth().height(160.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = consentToSend, onCheckedChange = { consentToSend = it })
                    Text("Acepto enviar esta foto al servidor de IA configurado. La estimación puede ser imprecisa.", color = MutedText, fontSize = 11.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onAnalyze(photoPath) }, enabled = !aiLoading && consentToSend, modifier = Modifier.weight(1f)) { Text(if (aiLoading) "Analizando…" else "Analizar con IA") }
                    OutlinedButton(onClick = onClearPhoto) { Text("Quitar") }
                }
            }
            AiResponse(aiLoading, aiError, aiResult)
            Text("Si no configuras el servicio de IA, puedes registrar la comida manualmente.", color = MutedText, fontSize = 11.sp)
        }
        Panel {
            Text("Añadir comida manualmente", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Field("Alimento o nombre de la comida", name, { name = it }, "Ej. arroz con habichuelas")
            Field("Porción aproximada", portion, { portion = it }, "Ej. 1 plato mediano")
            Text("Valores opcionales (introducidos por ti)", fontSize = 12.sp, color = MutedText)
            Field("Calorías estimadas", calories, { calories = it }, "Opcional", KeyboardType.Number)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) { Field("Proteína (g)", protein, { protein = it }, "", KeyboardType.Decimal) }
                Column(Modifier.weight(1f)) { Field("Carbohidratos (g)", carbs, { carbs = it }, "", KeyboardType.Decimal) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) { Field("Grasas (g)", fat, { fat = it }, "", KeyboardType.Decimal) }
                Column(Modifier.weight(1f)) { Field("Azúcares (g)", sugar, { sugar = it }, "", KeyboardType.Decimal) }
            }
            Field("Fibra (g)", fiber, { fiber = it }, "Opcional", KeyboardType.Decimal)
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            Button(onClick = {
                if (name.isBlank()) error = "Escribe el nombre del alimento." else {
                    onAddMeal(MealEntry(name = name.trim(), portion = portion.trim(), calories = calories.toIntOrNull()?.takeIf { it >= 0 }, protein = protein.toDoubleOrNull()?.takeIf { it >= 0 }, carbs = carbs.toDoubleOrNull()?.takeIf { it >= 0 }, fat = fat.toDoubleOrNull()?.takeIf { it >= 0 }, sugar = sugar.toDoubleOrNull()?.takeIf { it >= 0 }, fiber = fiber.toDoubleOrNull()?.takeIf { it >= 0 }, photoPath = photoPath))
                    name = ""; portion = ""; calories = ""; protein = ""; carbs = ""; fat = ""; sugar = ""; fiber = ""; error = ""
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Guardar comida · +5 XP") }
        }
        Panel {
            Text("Registro de hoy · ${todayMeals.size} comidas", fontWeight = FontWeight.Bold)
            if (todayMeals.isEmpty()) Text("Todavía no has registrado comidas hoy.", color = MutedText, fontSize = 12.sp)
            todayMeals.reversed().forEach { meal ->
                Column {
                    Text(meal.name, fontWeight = FontWeight.SemiBold)
                    Text(listOf(meal.portion, meal.time).filter { it.isNotBlank() }.joinToString(" · "), color = MutedText, fontSize = 11.sp)
                    val details = listOfNotNull(meal.protein?.let { "Proteína ${it}g" }, meal.carbs?.let { "Carbohidratos ${it}g" }, meal.fat?.let { "Grasas ${it}g" }, meal.fiber?.let { "Fibra ${it}g" }, meal.calories?.let { "$it kcal aprox." })
                    if (details.isNotEmpty()) Text(details.joinToString(" · "), color = MutedText, fontSize = 11.sp)
                    Divider(color = MutedText.copy(alpha = .14f))
                }
            }
        }
        Panel {
            Text("Historial reciente", fontWeight = FontWeight.Bold)
            val recentMeals = data.meals.sortedByDescending { it.date + it.time }.take(14)
            if (recentMeals.isEmpty()) Text("Aquí aparecerán tus registros anteriores.", color = MutedText, fontSize = 12.sp)
            recentMeals.forEach { meal ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (meal.photoPath.isNotBlank()) PrivatePhoto(meal.photoPath, Modifier.size(48.dp))
                    Column(Modifier.weight(1f).padding(start = if (meal.photoPath.isNotBlank()) 9.dp else 0.dp)) {
                        Text(meal.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("${meal.date} · ${meal.portion}", color = MutedText, fontSize = 10.sp)
                    }
                    meal.calories?.let { Text("~$it kcal", color = MutedText, fontSize = 11.sp) }
                }
                Divider(color = MutedText.copy(alpha = .14f))
            }
        }
        Panel {
            Text("Aprende sobre alimentación", fontWeight = FontWeight.Bold)
            Text("Una alimentación equilibrada incluye variedad de alimentos. Proteínas, carbohidratos y grasas cumplen funciones importantes; la fibra, vitaminas y minerales también forman parte de una buena alimentación.", color = MutedText, fontSize = 12.sp)
            Text("No se establecen objetivos de calorías, restricciones ni metas de pérdida de peso. Los datos son informativos y opcionales.", color = MutedText, fontSize = 11.sp)
        }
    }
}

@Composable
private fun EvolutionScreen(data: AppData, onBack: () -> Unit) {
    val active = ProgressRules.activeDates(data.completions)
    val today = LocalDate.now()
    val week = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val weekCounts = week.map { date -> data.completions.count { it.startsWith("$date|") } }
    val trainingCount = data.trainingSessions.size
    val careDays = active.count { date ->
        listOf("face_am_clean", "face_am_moist", "face_am_spf", "face_pm_clean", "face_pm_moist", "face_pm_check").all { "${date}|$it" in data.completions }
    }
    ScreenColumn {
        BackHeading("MY EVOLUTION", "Mi evolución", onBack)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("XP", data.xp.toString(), "Experiencia total", Modifier.weight(1f))
            MetricCard("DÍAS ACTIVOS", active.size.toString(), "Días con hábitos", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("ENTRENOS", trainingCount.toString(), "Sesiones guardadas", Modifier.weight(1f))
            MetricCard("RACHA", "${ProgressRules.currentStreak(data.completions)}", "Días actuales", Modifier.weight(1f))
        }
        Panel {
            Text("Actividad de los últimos 7 días", fontWeight = FontWeight.Bold)
            week.forEachIndexed { index, date ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale("es")), modifier = Modifier.width(36.dp), color = MutedText, fontSize = 11.sp)
                    LinearProgressIndicator(progress = (weekCounts[index] / 8f).coerceIn(0f, 1f), modifier = Modifier.weight(1f).height(8.dp).clip(CircleShape))
                    Text("${weekCounts[index]}", modifier = Modifier.width(26.dp), color = MutedText, fontSize = 11.sp)
                }
            }
        }
        Panel {
            Text("Objetivos basados en hábitos", fontWeight = FontWeight.Bold)
            Text("Días con rutina de cuidado completa: $careDays", color = MutedText, fontSize = 12.sp)
            Text("Comidas registradas: ${data.meals.size}", color = MutedText, fontSize = 12.sp)
            Text("Tu evolución se calcula a partir de las actividades guardadas, no del peso ni de juicios sobre tu aspecto.", color = MutedText, fontSize = 11.sp)
        }
    }
}

@Composable
private fun LevelsScreen(data: AppData, onBack: () -> Unit) {
    val level = ProgressRules.levelFor(data.xp)
    val active = ProgressRules.activeDates(data.completions)
    val today = LocalDate.now()
    val seven = (0..6).all { today.minusDays(it.toLong()) in active }
    val careDays = active.count { date -> listOf("face_am_clean", "face_am_moist", "face_am_spf", "face_pm_clean", "face_pm_moist", "face_pm_check").all { "${date}|$it" in data.completions } }
    data class Badge(val name: String, val detail: String, val unlocked: Boolean)
    val badges = listOf(
        Badge("First Step", "Completa tu primer hábito", data.completions.isNotEmpty()),
        Badge("Seven Days", "Mantén actividad durante siete días", ProgressRules.currentStreak(data.completions) >= 7),
        Badge("Training Rookie", "Registra cinco entrenamientos", data.trainingSessions.size >= 5),
        Badge("Face Care", "Completa diez rutinas de cuidado", careDays >= 10),
        Badge("Nutrition Explorer", "Registra diez comidas", data.meals.size >= 10),
        Badge("Consistency", "Actividad en los últimos siete días", seven),
        Badge("Dedicated", "Alcanza treinta días activos", active.size >= 30)
    )
    ScreenColumn {
        BackHeading("LEVELS & BADGES", "Niveles e insignias", onBack)
        Panel {
            Text("NIVEL ${level + 1}", color = AccentBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(ProgressRules.levelNames[level], fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("${data.xp} XP acumulados", color = MutedText)
            val next = if (level < ProgressRules.thresholds.lastIndex) ProgressRules.thresholds[level + 1] else ProgressRules.thresholds.last()
            val current = ProgressRules.thresholds[level]
            val progress = if (level == ProgressRules.thresholds.lastIndex) 1f else ((data.xp - current).toFloat() / (next - current)).coerceIn(0f, 1f)
            LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth().height(9.dp).clip(CircleShape))
            Text(if (level == ProgressRules.thresholds.lastIndex) "Has alcanzado el nivel máximo actual." else "${next - data.xp} XP para el siguiente nivel", color = MutedText, fontSize = 11.sp)
        }
        Panel {
            Text("Galería de insignias", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            badges.forEach { badge ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(if (badge.unlocked) AccentViolet.copy(alpha = .2f) else MutedText.copy(alpha = .1f)), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Icon(Icons.Filled.Star, contentDescription = null, tint = if (badge.unlocked) AccentViolet else MutedText)
                    }
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(badge.name, fontWeight = FontWeight.SemiBold)
                        Text(badge.detail, color = MutedText, fontSize = 11.sp)
                    }
                    Text(if (badge.unlocked) "Desbloqueada" else "Bloqueada", color = if (badge.unlocked) AccentBlue else MutedText, fontSize = 10.sp)
                }
            }
        }
        Text("Descansar no elimina tus logros. Las insignias se desbloquean con tus registros reales.", color = MutedText, fontSize = 11.sp)
    }
}

@Composable
private fun CalendarScreen(data: AppData, onBack: () -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    val first = month.atDay(1)
    val leading = first.dayOfWeek.value % 7
    val total = month.lengthOfMonth()
    val dates: List<LocalDate?> = (0 until leading).map { null } + (1..total).map { month.atDay(it) }
    ScreenColumn {
        BackHeading("CALENDAR", "Calendario", onBack)
        Panel {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹") }
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es"))).replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold)
                TextButton(onClick = { month = month.plusMonths(1) }) { Text("›") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                listOf("D", "L", "M", "X", "J", "V", "S").forEach { Text(it, color = MutedText, fontSize = 11.sp) }
            }
            dates.chunked(7).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    row.forEach { date ->
                        Box(Modifier.weight(1f).height(42.dp), contentAlignment = Alignment.Center) {
                            if (date != null) {
                                val hasActivity = ProgressRules.activeDates(data.completions).contains(date) || date.toString() in data.trainingSessions
                                Box(Modifier.size(32.dp).clip(CircleShape).background(if (hasActivity) AccentBlue.copy(alpha = .22f) else Color.Transparent), contentAlignment = Alignment.Center) {
                                    Text(date.dayOfMonth.toString(), color = if (hasActivity) AccentBlue else MaterialTheme.colorScheme.onSurface, fontWeight = if (hasActivity) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(AccentBlue))
                Text("  Día con actividad registrada", color = MutedText, fontSize = 11.sp)
            }
        }
        Panel {
            Text("Resumen del mes", fontWeight = FontWeight.Bold)
            val activeThisMonth = ProgressRules.activeDates(data.completions).count { it.year == month.year && it.month == month.month }
            val workoutsThisMonth = data.trainingSessions.count { runCatching { LocalDate.parse(it) }.getOrNull()?.let { date -> date.year == month.year && date.month == month.month } == true }
            Text("Días activos: $activeThisMonth · Entrenamientos: $workoutsThisMonth", color = MutedText, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MoreScreen(onNavigate: (String) -> Unit, data: AppData) {
    ScreenColumn {
        ScreenHeading("TU ESPACIO", "Más herramientas", "Consulta tu progreso y configura la aplicación.")
        MoreTile("My Evolution", "Estadísticas y constancia", Icons.Filled.BarChart) { onNavigate("Evolution") }
        MoreTile("Levels & Badges", "Experiencia e insignias", Icons.Filled.Star) { onNavigate("Levels") }
        MoreTile("Calendar", "Historial de actividades", Icons.Filled.CalendarMonth) { onNavigate("Calendar") }
        MoreTile("Profile & Settings", "Datos privados, recordatorios y tema", Icons.Filled.Settings) { onNavigate("Profile") }
        Panel {
            Text("Privacidad", fontWeight = FontWeight.Bold)
            Text("Tu perfil, registros y fotografías se guardan localmente en este dispositivo. No existe una cuenta pública ni funciones sociales.", color = MutedText, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MoreTile(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(17.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(icon, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f).padding(start = 13.dp)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, color = MutedText, fontSize = 11.sp) }
            Text("›", fontSize = 24.sp, color = MutedText)
        }
    }
}

@Composable
private fun ProfileScreen(
    data: AppData,
    onBack: () -> Unit,
    onProfileChange: (UserProfile) -> Unit,
    onReminderChange: (ReminderSetting) -> Unit,
    onExport: () -> Unit,
    onDeleteAll: () -> Unit
) {
    val context = LocalContext.current
    var notificationPermissionMessage by remember { mutableStateOf("") }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationPermissionMessage = if (granted) "Permiso de notificaciones concedido." else "Permiso denegado. Los recordatorios seguirán visibles dentro de la app, pero Android no mostrará avisos."
        if (granted) ReminderScheduler.scheduleAll(context, data.reminders)
    }
    val profile = data.profile ?: return
    var name by remember(profile.name) { mutableStateOf(profile.name) }
    var age by remember(profile.age) { mutableStateOf(profile.age.toString()) }
    var height by remember(profile.heightCm) { mutableStateOf(profile.heightCm.toString()) }
    var weight by remember(profile.weightKg) { mutableStateOf(profile.weightKg?.toString() ?: "") }
    var daily by remember(profile.dailyMinutes) { mutableStateOf(profile.dailyMinutes.toString()) }
    var hairType by remember(profile.hairType) { mutableStateOf(profile.hairType) }
    var hairStyle by remember(profile.hairStyle) { mutableStateOf(profile.hairStyle) }
    var daysPerWeek by remember(profile.daysPerWeek) { mutableStateOf(profile.daysPerWeek.toString()) }
    var goals by remember(profile.goals) { mutableStateOf(profile.goals.toSet()) }
    var showDelete by remember { mutableStateOf(false) }
    var savedMessage by remember { mutableStateOf("") }
    ScreenColumn {
        BackHeading("PROFILE", "Perfil y ajustes", onBack)
        Panel {
            Text("Información personal", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Estos datos permanecen en el almacenamiento privado de esta app.", color = MutedText, fontSize = 11.sp)
            Field("Nombre", name, { name = it })
            Field("Edad", age, { age = it }, keyboard = KeyboardType.Number)
            Field("Estatura (cm)", height, { height = it }, keyboard = KeyboardType.Number)
            Field("Peso (kg, opcional)", weight, { weight = it }, keyboard = KeyboardType.Decimal)
            Field("Tiempo diario (min)", daily, { daily = it }, keyboard = KeyboardType.Number)
            Field("Tipo de cabello (opcional)", hairType, { hairType = it })
            Field("Peinado que prefieres (opcional)", hairStyle, { hairStyle = it })
            Field("Días de entrenamiento por semana", daysPerWeek, { daysPerWeek = it }, keyboard = KeyboardType.Number)
            Text("Objetivos personales", color = MutedText, fontSize = 12.sp)
            listOf("Cuidado personal", "Fuerza", "Coordinación", "Movilidad y flexibilidad", "Resistencia", "Cuidado de la piel", "Rutina capilar", "Dormir mejor", "Constancia y disciplina", "Alimentación variada").forEach { goal ->
                Row(Modifier.fillMaxWidth().clickable { goals = if (goal in goals) goals - goal else goals + goal }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = goal in goals, onCheckedChange = { checked -> goals = if (checked) goals + goal else goals - goal })
                    Text(goal, fontSize = 12.sp)
                }
            }
            Button(onClick = {
                if (name.isNotBlank() && (age.toIntOrNull() ?: 0) in 10..100 && (height.toIntOrNull() ?: 0) in 80..240) {
                    onProfileChange(profile.copy(name = name.trim(), age = age.toInt(), heightCm = height.toInt(), weightKg = weight.replace(',', '.').toDoubleOrNull()?.takeIf { it in 20.0..350.0 }, dailyMinutes = (daily.toIntOrNull() ?: 30).coerceIn(5, 240), hairType = hairType, hairStyle = hairStyle, daysPerWeek = (daysPerWeek.toIntOrNull() ?: 3).coerceIn(1, 7), goals = goals.toList()))
                    savedMessage = "Cambios guardados."
                } else savedMessage = "Revisa los campos antes de guardar."
            }, modifier = Modifier.fillMaxWidth()) { Text("Guardar perfil") }
            if (savedMessage.isNotBlank()) Text(savedMessage, color = AccentBlue, fontSize = 11.sp)
        }
        Panel {
            Text("Preferencias", fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Tema oscuro", fontWeight = FontWeight.SemiBold); Text("Ajusta la apariencia de la app.", color = MutedText, fontSize = 11.sp) }
                Switch(checked = profile.darkTheme, onCheckedChange = { onProfileChange(profile.copy(darkTheme = it)) })
            }
        }
        Panel {
            Text("Recordatorios del dispositivo", fontWeight = FontWeight.Bold)
            Text("Los avisos se programan en este teléfono. Android puede agruparlos o retrasarlos para ahorrar batería.", color = MutedText, fontSize = 11.sp)
            data.reminders.forEach { reminder ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(reminder.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(String.format(Locale.getDefault(), "%02d:%02d", reminder.hour, reminder.minute), color = MutedText, fontSize = 11.sp)
                    }
                    TextButton(onClick = {
                        TimePickerDialog(context, { _, h, m -> onReminderChange(reminder.copy(hour = h, minute = m)) }, reminder.hour, reminder.minute, true).show()
                    }) { Text("Hora") }
                    Switch(checked = reminder.enabled, onCheckedChange = { enabled ->
                        onReminderChange(reminder.copy(enabled = enabled))
                        if (enabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    })
                }
            }
            if (notificationPermissionMessage.isNotBlank()) Text(notificationPermissionMessage, color = MutedText, fontSize = 11.sp)
        }
        Panel {
            Text("Tus datos", fontWeight = FontWeight.Bold)
            Text("Puedes exportar una copia o eliminar definitivamente los registros y fotos guardados por esta instalación.", color = MutedText, fontSize = 11.sp)
            OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("Exportar copia JSON") }
            Button(onClick = { showDelete = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9B394B)), modifier = Modifier.fillMaxWidth()) { Text("Eliminar todos mis datos") }
        }
        Panel {
            Text("Integraciones", fontWeight = FontWeight.Bold)
            Text(if (BuildConfig.AI_BACKEND_URL.isBlank()) "IA: sin configurar. Para habilitar el análisis real de fotos, configura el backend siguiendo README.md. No se inventan análisis." else "Backend IA configurado: ${BuildConfig.AI_BACKEND_URL}", color = MutedText, fontSize = 11.sp)
            Text("Las claves del proveedor de IA deben permanecer en el servidor, nunca dentro de esta aplicación.", color = MutedText, fontSize = 11.sp)
        }
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("¿Eliminar todos tus datos?") },
            text = { Text("Se borrarán tu perfil, progreso, comidas, recordatorios y fotografías locales. Esta acción no se puede deshacer.") },
            confirmButton = { TextButton(onClick = { showDelete = false; onDeleteAll() }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun BackHeading(eyebrow: String, title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("‹ Volver") }
        Column(Modifier.weight(1f)) {
            Text(eyebrow, color = AccentBlue, fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
            Text(title, fontWeight = FontWeight.Bold, fontSize = 23.sp)
        }
    }
}

@Composable
private fun PrivatePhoto(path: String, modifier: Modifier = Modifier) {
    val bitmap = remember(path) { runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull() }
    if (bitmap != null) {
        Image(bitmap, contentDescription = "Fotografía privada", modifier = modifier.clip(RoundedCornerShape(15.dp)), contentScale = ContentScale.Crop)
    } else {
        Box(modifier.clip(RoundedCornerShape(15.dp)).background(MutedText.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            Text("Foto no disponible", color = MutedText, fontSize = 11.sp)
        }
    }
}


private fun importAsJpeg(context: android.content.Context, uri: Uri, destination: File) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    requireNotNull(context.contentResolver.openInputStream(uri)).use { input -> BitmapFactory.decodeStream(input, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "El archivo no es una imagen válida." }
    val sample = calculateSample(bounds.outWidth, bounds.outHeight, 1600)
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = requireNotNull(context.contentResolver.openInputStream(uri)).use { input -> BitmapFactory.decodeStream(input, null, options) }
        ?: error("No se pudo procesar la imagen.")
    try {
        destination.outputStream().use { output ->
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 84, output)) { "No se pudo guardar la fotografía." }
        }
    } finally { bitmap.recycle() }
}

private fun normalizeJpeg(file: File) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return
    val options = BitmapFactory.Options().apply { inSampleSize = calculateSample(bounds.outWidth, bounds.outHeight, 1600) }
    val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return
    val temp = File(file.parentFile, "${file.nameWithoutExtension}_compressed.jpg")
    try {
        temp.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 84, it) }
        bitmap.recycle()
        temp.copyTo(file, overwrite = true)
    } finally { temp.delete(); if (!bitmap.isRecycled) bitmap.recycle() }
}

private fun calculateSample(width: Int, height: Int, maxSide: Int): Int {
    var sample = 1
    while (width / sample > maxSide || height / sample > maxSide) sample *= 2
    return sample
}
