package com.medreminder.ui

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.medreminder.R
import com.medreminder.domain.model.DosageUnit
import com.medreminder.notification.Notifier
import com.medreminder.domain.model.Medication
import com.medreminder.domain.model.MedicationStatus
import com.medreminder.util.requestExactAlarmPermissionIntent
import dagger.hilt.android.AndroidEntryPoint
import java.text.DateFormat
import java.util.Date

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyLockScreenBehavior(intent)
        setContent { MedReminderTheme(viewModel) { MedReminderApp(viewModel) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyLockScreenBehavior(intent)
    }

    /**
     * Medication data must not appear over the lock screen in normal use. The
     * activity is only allowed to show above the keyguard and wake the screen
     * when it was launched by a full-screen alarm notification.
     */
    private fun applyLockScreenBehavior(intent: Intent?) {
        val fromAlarm = intent?.getBooleanExtra(Notifier.EXTRA_FULLSCREEN, false) == true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(fromAlarm)
            setTurnScreenOn(fromAlarm)
        } else {
            val flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            if (fromAlarm) window.addFlags(flags) else window.clearFlags(flags)
        }
    }
}

private enum class Screen { TODAY, MEDICATIONS, STATS, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedReminderApp(vm: MainViewModel) {
    var screen by remember { mutableStateOf(Screen.TODAY) }
    var showAdd by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val medications by vm.medications.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val exportBackup = rememberLauncherForActivityResult(CreateDocument("application/json")) { uri ->
        uri?.let(vm::exportBackup)
    }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importBackup)
    }
    if (Build.VERSION.SDK_INT >= 33) {
        // The launcher is intentionally created here; it is invoked from Settings.
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        floatingActionButton = {
            if (screen == Screen.MEDICATIONS) {
                FloatingActionButton(onClick = { showAdd = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_medication))
                }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = screen == Screen.TODAY,
                    onClick = { screen = Screen.TODAY },
                    icon = { Icon(Icons.Default.EventNote, null) },
                    label = { Text(stringResource(R.string.nav_today)) },
                )
                NavigationBarItem(
                    selected = screen == Screen.MEDICATIONS,
                    onClick = { screen = Screen.MEDICATIONS },
                    icon = { Icon(Icons.Default.Medication, null) },
                    label = { Text(stringResource(R.string.nav_medicines)) },
                )
                NavigationBarItem(
                    selected = screen == Screen.STATS,
                    onClick = { screen = Screen.STATS },
                    icon = { Icon(Icons.Default.EventNote, null) },
                    label = { Text(stringResource(R.string.nav_stats)) },
                )
                NavigationBarItem(
                    selected = screen == Screen.SETTINGS,
                    onClick = { screen = Screen.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text(stringResource(R.string.nav_settings)) },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            when (screen) {
                Screen.TODAY -> TodayScreen(today, vm)
                Screen.MEDICATIONS -> MedicationScreen(medications, vm)
                Screen.STATS -> StatsScreen(
                    stats.totalScheduled,
                    stats.taken,
                    stats.skipped,
                    stats.missed,
                    stats.adherencePercent,
                )
                Screen.SETTINGS -> SettingsScreen(
                    settings = settings,
                    onExport = { exportBackup.launch("med-reminder-backup.json") },
                    onImport = { importBackup.launch(arrayOf("application/json", "text/plain")) },
                    onNotifications = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onExactAlarms = {
                        ContextCompat.startActivity(context, context.requestExactAlarmPermissionIntent(), null)
                    },
                    onFullScreen = { enabled ->
                        vm.updateSettings { it.copy(fullScreenAlarm = enabled) }
                    },
                )
            }
        }
    }

    if (showAdd) {
        AddMedicationDialog(
            onDismiss = { showAdd = false },
            onSave = { name, dosage, unit, hour, minute ->
                vm.addMedication(name, dosage, unit, kotlinx.datetime.LocalTime(hour, minute))
                showAdd = false
            },
        )
    }
}

@Composable
private fun TodayScreen(rows: List<TodayRow>, vm: MainViewModel) {
    if (rows.isEmpty()) {
        EmptyState(stringResource(R.string.empty_today))
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
    ) {
        items(rows, key = { it.dose.id }) { row ->
            val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(row.dose.scheduledAtMillis))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        row.medication.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("${com.medreminder.util.formatDecimal(row.medication.dosageValue)} ${row.medication.dosageUnit.key} • $time")
                    Text(
                        doseStatusLabel(row.dose.status.key),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (row.dose.status.key == "scheduled") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            IconButton(onClick = { vm.take(row.dose.id) }) { Icon(Icons.Default.Check, stringResource(R.string.action_taken)) }
                            IconButton(onClick = { vm.snooze(row.dose.id) }) { Icon(Icons.Default.Snooze, stringResource(R.string.action_snooze)) }
                            IconButton(onClick = { vm.skip(row.dose.id) }) { Icon(Icons.Default.SkipNext, stringResource(R.string.action_skip)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MedicationScreen(medications: List<Medication>, vm: MainViewModel) {
    if (medications.isEmpty()) {
        EmptyState(stringResource(R.string.empty_medications))
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
    ) {
        items(medications, key = { it.id }) { med ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(med.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("${com.medreminder.util.formatDecimal(med.dosageValue)} ${med.dosageUnit.key} • ${med.form.key}")
                        Text(stringResource(if (med.status == MedicationStatus.ACTIVE) R.string.status_active else R.string.status_paused))
                        med.stockCount?.let { Text(stringResource(R.string.stock_format, it, med.refillThreshold)) }
                    }
                    IconButton(onClick = { vm.setActive(med, med.status != MedicationStatus.ACTIVE) }) {
                        Icon(
                            if (med.status == MedicationStatus.ACTIVE) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.toggle_medication),
                        )
                    }
                    IconButton(onClick = { vm.deleteMedication(med) }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsScreen(total: Int, taken: Int, skipped: Int, missed: Int, adherence: Int) {
    Column(Modifier.padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.nav_today), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.adherence_format, adherence), style = MaterialTheme.typography.displaySmall)
        StatCard(stringResource(R.string.stat_scheduled), total)
        StatCard(stringResource(R.string.action_taken), taken)
        StatCard(stringResource(R.string.stat_skipped), skipped)
        StatCard(stringResource(R.string.stat_missed), missed)
    }
}

@Composable
private fun StatCard(label: String, value: Int) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(value.toString(), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SettingsScreen(
    settings: com.medreminder.domain.model.AppSettings,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onNotifications: () -> Unit,
    onExactAlarms: () -> Unit,
    onFullScreen: (Boolean) -> Unit,
) {
    Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.backup_description),
            style = MaterialTheme.typography.bodySmall,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onExport, Modifier.weight(1f)) { Text(stringResource(R.string.export)) }
            OutlinedButton(onClick = onImport, Modifier.weight(1f)) { Text(stringResource(R.string.import_label)) }
        }
        Text(stringResource(R.string.reliability_title), style = MaterialTheme.typography.headlineSmall)
        OutlinedButton(onClick = onNotifications, Modifier.fillMaxWidth()) { Text(stringResource(R.string.allow_notifications)) }
        OutlinedButton(onClick = onExactAlarms, Modifier.fillMaxWidth()) { Text(stringResource(R.string.configure_exact_alarms)) }
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.full_screen_alarm))
                    Text(stringResource(R.string.full_screen_alarm_hint))
                }
                Switch(checked = settings.fullScreenAlarm, onCheckedChange = onFullScreen)
            }
        }
        Text(
            stringResource(R.string.privacy_note),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun AddMedicationDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, DosageUnit, Int, Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var dosage by remember { mutableStateOf("1") }
    var hour by remember { mutableStateOf(8) }
    var minute by remember { mutableStateOf(0) }
    var unit by remember { mutableStateOf(DosageUnit.TABLET) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_medication)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.field_name)) }, singleLine = true)
                OutlinedTextField(dosage, { dosage = it }, label = { Text(stringResource(R.string.field_dose)) }, singleLine = true)
                Text(stringResource(R.string.unit_format, unit.key))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DosageUnit.values().take(4).forEach {
                        TextButton(onClick = { unit = it }) { Text(it.key) }
                    }
                }
                OutlinedButton(onClick = {
                    TimePickerDialog(context, { _, h, m -> hour = h; minute = m }, hour, minute, true).show()
                }) { Text(stringResource(R.string.time_format, hour, minute)) }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && dosage.toDoubleOrNull()?.let { it > 0 } == true,
                onClick = { onSave(name, dosage, unit, hour, minute) },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun doseStatusLabel(key: String): String = stringResource(
    when (key) {
        "scheduled" -> R.string.status_scheduled
        "taken" -> R.string.action_taken
        "skipped" -> R.string.stat_skipped
        "missed" -> R.string.stat_missed
        else -> R.string.status_unknown
    },
)

@Composable
private fun EmptyState(message: String) {
    Column(
        Modifier.fillMaxWidth().padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun MedReminderTheme(vm: MainViewModel, content: @Composable () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val dark = when (settings.themeMode) {
        2 -> true
        1 -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
