package com.oddley.next

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oddley.next.app.NextApplication
import com.oddley.next.notification.TopTaskService
import com.oddley.next.ui.ListScreen
import com.oddley.next.ui.ListViewModel
import com.oddley.next.ui.theme.NextTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* permission result — service starts regardless; notification appears if granted */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as NextApplication
        val viewModel = ViewModelProvider(
            this,
            ListViewModel.Factory(application, app.taskRepository, app.emitterRepository, app.uiPrefsRepository),
        )[ListViewModel::class.java]

        // Request POST_NOTIFICATIONS on Android 13+ then start the foreground service
        requestNotificationPermissionIfNeeded()
        TopTaskService.start(this)

        enableEdgeToEdge()
        setContent {
            NextTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                ListScreen(
                    uiState = uiState,
                    onAddTask = viewModel::addTask,
                    onCrossOff = viewModel::crossOff,
                    onRestore = viewModel::restore,
                    onEditText = viewModel::editText,
                    onReorder = viewModel::reorder,
                    onBulkDeleteCrossedOff = viewModel::bulkDeleteCrossedOff,
                    onToggleTasks = viewModel::toggleTasksExpanded,
                    onToggleEmitters = viewModel::toggleEmittersExpanded,
                    onToggleCompleted = viewModel::toggleCompletedExpanded,
                    onAddEmitter = viewModel::addEmitter,
                    onUpdateEmitter = viewModel::updateEmitter,
                    onDeleteEmitter = viewModel::deleteEmitter,
                )
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) return
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
