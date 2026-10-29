package com.marvindi.mediaoptimizer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.marvindi.mediaoptimizer.R
import com.marvindi.mediaoptimizer.model.OutputFormat
import com.marvindi.mediaoptimizer.viewmodel.CompressViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: CompressViewModel) {

    // System photo picker: no storage permission required on any Android version.
    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.addUris(listOf(it)) }
    }

    var formatMenuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        pickMedia.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    enabled = !viewModel.isProcessing,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.select_photos))
                }
                if (viewModel.uris.isNotEmpty()) {
                    TextButton(
                        onClick = { viewModel.clear() },
                        enabled = !viewModel.isProcessing
                    ) {
                        Text(stringResource(R.string.clear))
                    }
                }
            }

            Text(
                text = stringResource(R.string.selected_count, viewModel.uris.size),
                style = MaterialTheme.typography.bodyMedium
            )

            if (viewModel.uris.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 72.dp),
                    modifier = Modifier
                        .height(240.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(viewModel.uris) { uri ->
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.output_format),
                    modifier = Modifier.weight(1f)
                )
                Box {
                    OutlinedButton(onClick = { formatMenuOpen = true }) {
                        Text(viewModel.format.displayName)
                    }
                    DropdownMenu(
                        expanded = formatMenuOpen,
                        onDismissRequest = { formatMenuOpen = false }
                    ) {
                        OutputFormat.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.displayName) },
                                onClick = {
                                    viewModel.setFormat(option)
                                    formatMenuOpen = false
                                }
                            )
                        }
                    }
                }
            }

            Column {
                Text(
                    text = "${stringResource(R.string.quality)}: ${viewModel.quality.toInt()}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = viewModel.quality,
                    onValueChange = { viewModel.setQuality(it) },
                    valueRange = 60f..100f,
                    enabled = !viewModel.isProcessing
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = viewModel.preserveExif,
                    onCheckedChange = { viewModel.setPreserveExif(it) },
                    enabled = !viewModel.isProcessing
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.preserve_exif),
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = stringResource(R.string.output_directory, viewModel.outputFolder),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = stringResource(R.string.originals_kept_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = { viewModel.start() },
                enabled = viewModel.uris.isNotEmpty() && !viewModel.isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (viewModel.isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.converting))
                } else {
                    Text(stringResource(R.string.start_compress))
                }
            }

            if (viewModel.isProcessing) {
                LinearProgressIndicator(
                    progress = { viewModel.progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${(viewModel.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            viewModel.statusMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
