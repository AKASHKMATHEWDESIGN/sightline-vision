package com.sightline.facestyle.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sightline.facestyle.ImageUtils
import com.sightline.facestyle.MainViewModel
import com.sightline.facestyle.PickedImage
import com.sightline.facestyle.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.apiKey) { if (state.apiKey.isBlank()) showSettings = true }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.dismissMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FaceStyle Studio", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (state.result != null) {
                ResultView(state, vm)
            } else {
                EditorView(state, vm)
            }
            if (state.loading) LoadingOverlay()
        }
    }

    if (showSettings) {
        SettingsDialog(state, onDismiss = { showSettings = false }) { key, model ->
            vm.saveSettings(key, model); showSettings = false
        }
    }
    state.error?.let { err ->
        AlertDialog(
            onDismissRequest = vm::dismissError,
            confirmButton = { TextButton(onClick = vm::dismissError) { Text("OK") } },
            title = { Text("Something went wrong") },
            text = { Text(err) },
        )
    }
}

@Composable
private fun EditorView(state: UiState, vm: MainViewModel) {
    val pickFaces = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MainViewModel.MAX_FACES),
    ) { uris -> if (uris.isNotEmpty()) vm.addFaces(uris) }
    val pickReference = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(vm::setReference) }
    val imagesOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Section(
            step = "1",
            title = "Your photos",
            hint = "Add 1–${MainViewModel.MAX_FACES} clear, well-lit photos of your face. Different angles improve likeness.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                state.faces.forEachIndexed { i, img ->
                    Thumb(img, Modifier.size(96.dp), onRemove = { vm.removeFace(i) })
                }
                if (state.faces.size < MainViewModel.MAX_FACES) {
                    AddTile(Modifier.size(96.dp), Icons.Default.AddAPhoto, "Add") { pickFaces.launch(imagesOnly) }
                }
            }
        }

        Section(
            step = "2",
            title = "Reference image",
            hint = "The look you want: its pose, outfit, colours, textures and mood will be copied.",
        ) {
            val ref = state.reference
            if (ref == null) {
                AddTile(Modifier.fillMaxWidth().height(180.dp), Icons.Default.Image, "Choose reference") {
                    pickReference.launch(imagesOnly)
                }
            } else {
                Box(
                    Modifier.fillMaxWidth().heightIn(max = 360.dp).clip(RoundedCornerShape(16.dp))
                        .clickable { pickReference.launch(imagesOnly) },
                ) {
                    Image(
                        ref.thumb.asImageBitmap(), contentDescription = "Reference",
                        modifier = Modifier.fillMaxWidth()
                            .aspectRatio(ref.thumb.width.toFloat() / ref.thumb.height, matchHeightConstraintsFirst = false),
                        contentScale = ContentScale.Crop,
                    )
                    Text(
                        "Tap to change", style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }

        Section(step = "3", title = "Options", hint = null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Keep my own hairstyle")
                    Text(
                        "Off = use the hairstyle from the reference",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.keepMyHair, onCheckedChange = vm::setKeepMyHair)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.extraPrompt, onValueChange = vm::setExtraPrompt,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Extra instructions (optional)") },
                placeholder = { Text("e.g. keep my glasses, make me smile slightly") },
                minLines = 2,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { vm.setConsent(!state.consent) }) {
            Checkbox(checked = state.consent, onCheckedChange = vm::setConsent)
            Text(
                "These photos are of me, or I have the person's permission to use their face.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Button(
            onClick = vm::generate, enabled = state.canGenerate,
            modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Default.AutoAwesome, null)
            Spacer(Modifier.width(8.dp))
            Text("Generate", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ResultView(state: UiState, vm: MainViewModel) {
    val context = LocalContext.current
    val result = state.result ?: return
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Image(
            result.asImageBitmap(), contentDescription = "Generated image",
            modifier = Modifier.fillMaxWidth().aspectRatio(result.width.toFloat() / result.height)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Fit,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = vm::saveResult, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Download, null); Spacer(Modifier.width(6.dp)); Text("Save")
            }
            Button(
                onClick = { context.startActivity(ImageUtils.shareIntent(context, result)) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("Share")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = vm::generate, modifier = Modifier.weight(1f), enabled = !state.loading) {
                Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Try again")
            }
            OutlinedButton(onClick = vm::clearResult, modifier = Modifier.weight(1f)) {
                Text("Edit inputs")
            }
        }
        Text(
            "AI-generated image. Each attempt differs — try again if the likeness isn't right, or add more photos of yourself.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Section(step: String, title: String, hint: String?, content: @Composable () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(26.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(step, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        if (hint != null) {
            Text(
                hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 36.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun Thumb(img: PickedImage, modifier: Modifier, onRemove: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(14.dp))) {
        Image(img.thumb.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        IconButton(
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp)
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.75f), CircleShape),
        ) {
            Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun AddTile(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun LoadingOverlay() {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f))
            .clickable(enabled = true, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Creating your image…", style = MaterialTheme.typography.titleMedium)
            Text(
                "This usually takes 10–60 seconds",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsDialog(state: UiState, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var key by rememberSaveable { mutableStateOf(state.apiKey) }
    var model by rememberSaveable { mutableStateOf(state.model) }
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Images are generated by Google's Gemini image model. Paste your API key from Google AI Studio.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { uriHandler.openUri("https://aistudio.google.com/apikey") }) {
                    Text("Get a free API key →")
                }
                OutlinedTextField(
                    value = key, onValueChange = { key = it }, singleLine = true,
                    label = { Text("Gemini API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("Model", style = MaterialTheme.typography.labelLarge)
                MainViewModel.MODEL_PRESETS.forEach { (id, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { model = id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = model == id, onClick = { model = id })
                        Column {
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                            Text(id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                OutlinedTextField(
                    value = model, onValueChange = { model = it }, singleLine = true,
                    label = { Text("Model ID (editable)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(key, model) }, enabled = key.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
