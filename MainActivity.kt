package com.example.barnameh12

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private val subjects = listOf(
    "زیست‌شناسی ۳", "شیمی ۳", "فیزیک ۳", "ریاضی ۳",
    "فارسی ۳", "نگارش ۳", "دین و زندگی ۳", "عربی، زبان قرآن ۳",
    "زبان انگلیسی ۳", "سلامت و بهداشت", "هویت اجتماعی",
    "زمین‌شناسی", "مرور و آزمون", "مطالعه آزاد"
)
private val days = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")
private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

data class StudyTask(
    val id: String = UUID.randomUUID().toString(),
    val week: String,
    val day: Int,
    val subject: String,
    val hour: Int,
    val minute: Int,
    val duration: Int,
    val done: Boolean = false
)


private fun loadGoals(context: Context): MutableMap<String, Int> {
    val raw = context.getSharedPreferences("planner", Context.MODE_PRIVATE).getString("goals", "{}") ?: "{}"
    val obj = JSONObject(raw)
    val result = mutableMapOf<String, Int>()
    val keys = obj.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        result[key] = obj.optInt(key, 0)
    }
    return result
}

private fun saveGoals(context: Context, goals: Map<String, Int>) {
    val obj = JSONObject()
    goals.forEach { (subject, hours) -> obj.put(subject, hours) }
    context.getSharedPreferences("planner", Context.MODE_PRIVATE).edit().putString("goals", obj.toString()).apply()
}

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { StudyPlanner(this) }
    }
}

private fun loadTasks(context: Context): MutableList<StudyTask> {
    val raw = context.getSharedPreferences("planner", Context.MODE_PRIVATE).getString("tasks", "[]") ?: "[]"
    val arr = JSONArray(raw)
    val result = mutableListOf<StudyTask>()
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        result.add(StudyTask(
            id = o.optString("id"), week = o.optString("week"), day = o.optInt("day"),
            subject = o.optString("subject"), hour = o.optInt("hour"), minute = o.optInt("minute"),
            duration = o.optInt("duration", 60), done = o.optBoolean("done")
        ))
    }
    return result
}

private fun saveTasks(context: Context, tasks: List<StudyTask>) {
    val arr = JSONArray()
    tasks.forEach { t ->
        arr.put(JSONObject().apply {
            put("id", t.id); put("week", t.week); put("day", t.day)
            put("subject", t.subject); put("hour", t.hour); put("minute", t.minute)
            put("duration", t.duration); put("done", t.done)
        })
    }
    context.getSharedPreferences("planner", Context.MODE_PRIVATE).edit().putString("tasks", arr.toString()).apply()
}

private fun scheduleReminder(context: Context, task: StudyTask) {
    val start = LocalDate.parse(task.week, formatter).plusDays(task.day.toLong())
    val dateTime = LocalDateTime.of(start, LocalTime.of(task.hour, task.minute))
    val millis = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    if (millis <= System.currentTimeMillis()) return
    val intent = Intent(context, ReminderReceiver::class.java).apply {
        putExtra("subject", task.subject)
        putExtra("duration", task.duration)
        putExtra("notificationId", task.id.hashCode())
    }
    val pending = PendingIntent.getBroadcast(
        context, task.id.hashCode(), intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyPlanner(context: Context) {
    var allTasks by remember { mutableStateOf(loadTasks(context)) }
    var weeklyGoals by remember { mutableStateOf(loadGoals(context)) }
    var showGoals by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val thisSaturday = today.minusDays((today.dayOfWeek.value % 7).toLong())
    var weekOffset by remember { mutableIntStateOf(0) }
    val weekStart = thisSaturday.plusWeeks(weekOffset.toLong())
    val weekKey = weekStart.format(formatter)
    val weekTasks = allTasks.filter { it.week == weekKey }.sortedWith(compareBy<StudyTask> { it.day }.thenBy { it.hour }.thenBy { it.minute })
    val completed = weekTasks.count { it.done }
    var showAdd by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableIntStateOf(0) }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFFD4AF37),
        onPrimary = androidx.compose.ui.graphics.Color(0xFF171717),
        secondary = androidx.compose.ui.graphics.Color(0xFFE6C65C),
        background = androidx.compose.ui.graphics.Color(0xFF101010),
        surface = androidx.compose.ui.graphics.Color(0xFF1B1B1B),
        onSurface = androidx.compose.ui.graphics.Color(0xFFF5F1E6),
        onBackground = androidx.compose.ui.graphics.Color(0xFFF5F1E6)
    )) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("برنامه‌ریز درسی دوازدهم", fontWeight = FontWeight.Bold) }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { selectedDay = 0; showAdd = true }) { Text("+") }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp)) {
                Card(Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("برنامه هفتگی", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { weekOffset-- }) { Text("هفته قبل") }
                            Text("${weekStart.format(formatter)} تا ${weekStart.plusDays(6).format(formatter)}")
                            TextButton(onClick = { weekOffset++ }) { Text("هفته بعد") }
                        }
                        val percent = if (weekTasks.isEmpty()) 0f else completed.toFloat() / weekTasks.size
                        LinearProgressIndicator(progress = { percent }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(5.dp))
                        Text("پیشرفت: $completed از ${weekTasks.size} برنامه انجام شده (${(percent * 100).toInt()}٪)")
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { showGoals = true }) { Text("تنظیم هدف‌های هفتگی") }
                    }
                }
                val activeGoals = weeklyGoals.filterValues { it > 0 }
                if (activeGoals.isNotEmpty()) {
                    Card(Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("هدف‌های این هفته", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            activeGoals.forEach { (subject, targetHours) ->
                                val studiedMinutes = weekTasks.filter { it.subject == subject && it.done }.sumOf { it.duration }
                                val targetMinutes = targetHours * 60
                                val goalProgress = (studiedMinutes.toFloat() / targetMinutes).coerceIn(0f, 1f)
                                Column {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(subject)
                                        Text("${studiedMinutes / 60}س ${studiedMinutes % 60}د / $targetHours ساعت")
                                    }
                                    LinearProgressIndicator(progress = { goalProgress }, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (weekTasks.isEmpty()) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp)) {
                            Text("هنوز برنامه‌ای برای این هفته ثبت نکردی.")
                            Text("برای اضافه‌کردن درس، دکمه + را بزن.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
                        days.forEachIndexed { index, day ->
                            val itemsForDay = weekTasks.filter { it.day == index }
                            if (itemsForDay.isNotEmpty()) {
                                item {
                                    Text(day, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                                }
                                items(itemsForDay, key = { it.id }) { task ->
                                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                                        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = task.done, onCheckedChange = { checked ->
                                                allTasks = allTasks.map { if (it.id == task.id) it.copy(done = checked) else it }.toMutableList()
                                                saveTasks(context, allTasks)
                                            })
                                            Column(Modifier.weight(1f)) {
                                                Text(task.subject, fontWeight = FontWeight.SemiBold)
                                                Text("${task.hour.toString().padStart(2, '0')}:${task.minute.toString().padStart(2, '0')}  •  ${task.duration} دقیقه",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                            }
                                            TextButton(onClick = {
                                                allTasks = allTasks.filterNot { it.id == task.id }.toMutableList()
                                                saveTasks(context, allTasks)
                                            }) { Text("حذف") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showGoals) {
            GoalsDialog(
                currentGoals = weeklyGoals,
                onDismiss = { showGoals = false },
                onSave = { updated ->
                    weeklyGoals = updated.toMutableMap()
                    saveGoals(context, weeklyGoals)
                    showGoals = false
                }
            )
        }
        if (showAdd) {
            AddTaskDialog(
                initialDay = selectedDay,
                onDismiss = { showAdd = false },
                onSave = { subject, day, hour, minute, duration ->
                    val task = StudyTask(week = weekKey, day = day, subject = subject, hour = hour, minute = minute, duration = duration)
                    allTasks = (allTasks + task).toMutableList()
                    saveTasks(context, allTasks)
                    scheduleReminder(context, task)
                    showAdd = false
                }
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalsDialog(
    currentGoals: Map<String, Int>,
    onDismiss: () -> Unit,
    onSave: (Map<String, Int>) -> Unit
) {
    var values by remember { mutableStateOf(currentGoals.toMutableMap()) }
    var selected by remember { mutableStateOf(subjects.first()) }
    var hours by remember { mutableStateOf((currentGoals[subjects.first()] ?: 0).toString()) }
    var expanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("هدف مطالعه هفتگی") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("برای هر درس، تعداد ساعت هدف در هفته را وارد کن. برای حذف هدف، عدد صفر را بگذار.")
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = selected, onValueChange = {}, readOnly = true,
                        label = { Text("درس") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        subjects.forEach { s ->
                            DropdownMenuItem(text = { Text(s) }, onClick = {
                                selected = s
                                hours = (values[s] ?: 0).toString()
                                expanded = false
                            })
                        }
                    }
                }
                OutlinedTextField(
                    value = hours, onValueChange = { hours = it.filter(Char::isDigit).take(2) },
                    label = { Text("هدف ساعت در هفته") }, modifier = Modifier.fillMaxWidth()
                )
                Text("هدف‌های ثبت‌شده: ${values.count { it.value > 0 }} درس")
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    val h = hours.toIntOrNull()
                    if (h == null || h !in 0..60) error = "عدد باید بین ۰ تا ۶۰ ساعت باشد."
                    else {
                        values[selected] = h
                        onSave(values.filterValues { it > 0 })
                    }
                }) { Text("ذخیره هدف") }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                val h = hours.toIntOrNull()
                if (h != null && h in 0..60) {
                    values[selected] = h
                    onSave(values.filterValues { it > 0 })
                } else onDismiss()
            }) { Text("بستن") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTaskDialog(
    initialDay: Int,
    onDismiss: () -> Unit,
    onSave: (String, Int, Int, Int, Int) -> Unit
) {
    var subject by remember { mutableStateOf(subjects.first()) }
    var day by remember { mutableIntStateOf(initialDay) }
    var hour by remember { mutableStateOf("16") }
    var minute by remember { mutableStateOf("00") }
    var duration by remember { mutableStateOf("60") }
    var subjectExpanded by remember { mutableStateOf(false) }
    var dayExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("افزودن برنامه درسی") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(expanded = subjectExpanded, onExpandedChange = { subjectExpanded = !subjectExpanded }) {
                    OutlinedTextField(value = subject, onValueChange = {}, readOnly = true, label = { Text("درس") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(subjectExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = subjectExpanded, onDismissRequest = { subjectExpanded = false }) {
                        subjects.forEach { s -> DropdownMenuItem(text = { Text(s) }, onClick = { subject = s; subjectExpanded = false }) }
                    }
                }
                ExposedDropdownMenuBox(expanded = dayExpanded, onExpandedChange = { dayExpanded = !dayExpanded }) {
                    OutlinedTextField(value = days[day], onValueChange = {}, readOnly = true, label = { Text("روز هفته") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dayExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = dayExpanded, onDismissRequest = { dayExpanded = false }) {
                        days.forEachIndexed { i, d -> DropdownMenuItem(text = { Text(d) }, onClick = { day = i; dayExpanded = false }) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(hour, { hour = it.filter(Char::isDigit).take(2) }, label = { Text("ساعت") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(minute, { minute = it.filter(Char::isDigit).take(2) }, label = { Text("دقیقه") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(duration, { duration = it.filter(Char::isDigit).take(3) }, label = { Text("مدت") }, modifier = Modifier.weight(1f))
                }
                Text("مدت بر حسب دقیقه است.", style = MaterialTheme.typography.bodySmall)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val h = hour.toIntOrNull()
                val m = minute.toIntOrNull()
                val d = duration.toIntOrNull()
                if (h == null || h !in 0..23 || m == null || m !in 0..59 || d == null || d !in 1..600) {
                    error = "ساعت، دقیقه یا مدت مطالعه معتبر نیست."
                } else onSave(subject, day, h, m, d)
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
