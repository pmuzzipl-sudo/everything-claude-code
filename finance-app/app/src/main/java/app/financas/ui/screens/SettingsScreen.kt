@file:OptIn(ExperimentalMaterial3Api::class)

package app.financas.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.financas.data.AppSettings
import app.financas.reminders.DueBillsWorker
import app.financas.security.AppLock

@Composable
fun SettingsScreen(
    accountEmail: String?,
    onSignOut: () -> Unit,
    brapiToken: String?,
    onSaveToken: (String?) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    var lockEnabled by remember { mutableStateOf(settings.lockEnabled) }
    var reminders by remember { mutableStateOf(settings.remindersEnabled) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var tokenInput by remember(brapiToken) { mutableStateOf(brapiToken.orEmpty()) }
    var tokenSaved by remember { mutableStateOf(false) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) message = "Sem permissão de notificação, os lembretes não aparecem. Ative nas configurações do Android."
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            SectionTitle("Segurança")
            SettingSwitch(
                title = "Bloquear o app",
                subtitle = "Pede a digital ou o PIN do aparelho ao abrir o app e ao voltar depois de 30 segundos fora.",
                checked = lockEnabled,
                onChange = { on ->
                    if (on && !AppLock.isAvailable(context)) {
                        message = "Configure um bloqueio de tela (PIN, padrão ou digital) nas configurações do Android primeiro."
                    } else {
                        if (on) AppLock.markUnlocked()
                        settings.lockEnabled = on
                        lockEnabled = on
                    }
                },
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionTitle("Lembretes")
            SettingSwitch(
                title = "Avisar contas a vencer",
                subtitle = "Notificação por volta das 9h com as contas não pagas que vencem hoje, amanhã ou estão atrasadas.",
                checked = reminders,
                onChange = { on ->
                    settings.remindersEnabled = on
                    reminders = on
                    if (on) {
                        DueBillsWorker.schedule(context)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    } else {
                        DueBillsWorker.cancel(context)
                    }
                },
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionTitle("Cotações (investimentos)")
            Text(
                "As cotações vêm do brapi.dev. Crie uma conta gratuita em brapi.dev, copie sua chave (token) " +
                    "no painel e cole abaixo. Ela fica salva na sua conta e vale para todos os aparelhos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = tokenInput,
                onValueChange = { tokenInput = it.trim(); tokenSaved = false },
                label = { Text("Chave do brapi.dev") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { onSaveToken(tokenInput.ifBlank { null }); tokenSaved = true },
                    enabled = tokenInput != brapiToken.orEmpty(),
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Salvar chave") }
                if (tokenSaved) Text("Salva!", Modifier.padding(start = 12.dp), color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionTitle("Conta")
            Text(
                "Sincronizado com ${accountEmail ?: "sua conta Google"}. Use a mesma conta nos outros aparelhos.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = { confirmSignOut = true }, modifier = Modifier.padding(top = 8.dp)) { Text("Sair da conta") }

            message?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sair da conta?") },
            text = { Text("Seus dados continuam salvos na nuvem. Entre de novo com a mesma conta para vê-los.") },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; onSignOut() }) { Text("Sair") } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
