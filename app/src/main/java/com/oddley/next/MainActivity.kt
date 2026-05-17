package com.oddley.next

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.oddley.next.app.NextApplication
import com.oddley.next.ui.ListScreen
import com.oddley.next.ui.ListViewModel
import com.oddley.next.ui.theme.NextTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = (application as NextApplication).taskRepository
        val viewModel = ViewModelProvider(
            this,
            ListViewModel.Factory(repository),
        )[ListViewModel::class.java]

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
                )
            }
        }
    }
}
