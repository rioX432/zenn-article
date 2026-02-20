package com.example.snackbaroverlay

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SnackbarDemo()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnackbarDemo() {
    var useOverlay by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = {
            // Before: 通常の SnackbarHost（ModalBottomSheet の裏に隠れる）
            if (!useOverlay) {
                SnackbarHost(hostState = snackbarHostState)
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = if (useOverlay) "After: Dialog Overlay" else "Before: Scaffold SnackbarHost",
                style = MaterialTheme.typography.headlineSmall,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Use Dialog Overlay")
                Spacer(modifier = Modifier.width(8.dp))
                Switch(checked = useOverlay, onCheckedChange = { useOverlay = it })
            }

            Button(onClick = { showSheet = true }) {
                Text("Show BottomSheet")
            }

            Button(onClick = {
                scope.launch {
                    snackbarHostState.showSnackbar("Hello from Snackbar!")
                }
            }) {
                Text("Show Snackbar")
            }
        }

        // After: Dialog overlay（ModalBottomSheet より上に表示される）
        if (useOverlay) {
            SnackbarOverlayDialog(snackbarHostState = snackbarHostState)
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            ) {
                Text(
                    text = "ModalBottomSheet",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("この状態で Snackbar を表示してみてください")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    scope.launch {
                        snackbarHostState.showSnackbar("Snackbar while sheet is open!")
                    }
                }) {
                    Text("Show Snackbar")
                }
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

/**
 * Snackbar overlay that displays above ModalBottomSheet dialogs.
 *
 * ModalBottomSheet creates a separate Dialog window, so a SnackbarHost inside
 * Scaffold is always hidden behind it. This composable wraps SnackbarHost in
 * its own Dialog with FLAG_NOT_FOCUSABLE + FLAG_NOT_TOUCHABLE so that:
 * - The snackbar appears above any ModalBottomSheet
 * - All touch events pass through to the windows below
 */
@Composable
fun SnackbarOverlayDialog(
    snackbarHostState: SnackbarHostState,
) {
    if (snackbarHostState.currentSnackbarData == null) return

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false,
        )
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.apply {
                setDimAmount(0f)
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                addFlags(
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 24.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            SnackbarHost(hostState = snackbarHostState)
        }
    }
}
