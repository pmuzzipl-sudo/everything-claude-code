package app.financas.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.financas.data.AppSettings
import app.financas.data.CloudRepository
import app.financas.data.FinanceRepository
import app.financas.data.LegacyData
import app.financas.data.LegacySnapshot
import app.financas.invest.InvestRepository
import app.financas.invest.InvestmentsViewModel
import app.financas.invest.QuoteCache
import app.financas.reminders.DueBillsWorker
import app.financas.security.AppLock
import app.financas.security.LockScreen
import app.financas.ui.auth.LoginScreen
import app.financas.ui.auth.userFlow
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bloqueio (se ativado) → login → app com os dados da conta. */
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    if (settings.lockEnabled && !AppLock.unlocked) {
        LockScreen()
        return
    }
    val auth = remember { FirebaseAuth.getInstance() }
    val user by remember { auth.userFlow() }.collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val current = user
    if (current == null) {
        LoginScreen()
        return
    }
    key(current.uid) {
        val repo = remember { CloudRepository(current.uid) }
        val vm: FinanceViewModel = viewModel(
            key = current.uid,
            factory = viewModelFactory { initializer { FinanceViewModel(repo) } },
        )
        val investVm: InvestmentsViewModel = viewModel(
            key = "invest-" + current.uid,
            factory = viewModelFactory {
                initializer { InvestmentsViewModel(InvestRepository(current.uid), QuoteCache(context)) }
            },
        )
        FinanceApp(vm = vm, investVm = investVm, accountEmail = current.email, onSignOut = { auth.signOut() })
        LegacyImportDialog(repo)
        RemindersSetup(settings)
    }
}

/**
 * Na primeira vez após a atualização, oferece enviar para a conta os dados que estavam salvos só neste aparelho.
 */
@Composable
private fun LegacyImportDialog(repo: FinanceRepository) {
    val context = LocalContext.current
    val legacy = remember { LegacyData(context.applicationContext) }
    var pending by remember { mutableStateOf<LegacySnapshot?>(null) }

    LaunchedEffect(Unit) {
        if (legacy.alreadyHandled) return@LaunchedEffect
        val snapshot = withContext(Dispatchers.IO) { runCatching { legacy.load() }.getOrNull() } ?: return@LaunchedEffect
        if (snapshot.isEmpty) legacy.markHandled() else pending = snapshot
    }

    val snapshot = pending ?: return
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("Enviar dados deste aparelho?") },
        text = {
            Text(
                "Encontramos dados salvos neste aparelho antes da sincronização: " +
                    "${snapshot.transactions.size} lançamentos, ${snapshot.fixedExpenses.size} despesas fixas e " +
                    "${snapshot.budgets.size} orçamentos.\n\n" +
                    "Envie para sua conta para que apareçam em todos os aparelhos. " +
                    "Se não enviar, eles deixam de aparecer no app."
            )
        },
        confirmButton = {
            TextButton(onClick = {
                repo.importLegacy(snapshot.withCloudIds(legacy.salt()))
                legacy.markHandled()
                pending = null
            }) { Text("Enviar") }
        },
        dismissButton = {
            TextButton(onClick = {
                legacy.markHandled()
                pending = null
            }) { Text("Não enviar") }
        },
    )
}

/** Agenda o lembrete diário e, na primeira vez, pede permissão de notificação (Android 13+). */
@Composable
private fun RemindersSetup(settings: AppSettings) {
    val context = LocalContext.current
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (!settings.remindersEnabled) return@LaunchedEffect
        DueBillsWorker.schedule(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !settings.askedNotificationPermission &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            settings.askedNotificationPermission = true
            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
