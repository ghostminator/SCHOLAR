package com.ghostminator.scholar.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ghostminator.scholar.core.AiHelper
import kotlinx.coroutines.launch

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
)

@Composable
fun MainApp(
    aiHelper: AiHelper = remember { AiHelper() },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val openPdfs = remember { mutableStateListOf<Uri>() }
    val pdfNames = remember { mutableStateMapOf<Uri, String>() }
    var activeIndex by rememberSaveable { mutableIntStateOf(-1) }
    var chatInput by rememberSaveable { mutableStateOf("") }
    var isIngesting by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var enableWebSearch by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var pdfPaneWidthRatio by rememberSaveable { mutableFloatStateOf(0.58f) }
    var pdfPaneHeightRatio by rememberSaveable { mutableFloatStateOf(0.55f) }

    val pdfMimeTypes = arrayOf("application/pdf")
    val activeUri = openPdfs.getOrNull(activeIndex)
    val activeName = activeUri?.let { pdfNames[it] } ?: "PDF Viewer"

    fun ingestPdf(uri: Uri) {
        scope.launch {
            isIngesting = true
            error = null
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Could not read the selected PDF.")

                val name = pdfNames[uri] ?: getFileName(context, uri)
                aiHelper.loadPdf(uri.toString(), bytes)
                messages.add(
                    ChatMessage(
                        text = "Loaded \"$name\". Ask me anything about it.",
                        isUser = false,
                    ),
                )
            } catch (e: Exception) {
                error = e.message ?: "Failed to analyze the PDF."
                openPdfs.remove(uri)
                pdfNames.remove(uri)
                if (activeIndex >= openPdfs.size) {
                    activeIndex = openPdfs.lastIndex
                }
            } finally {
                isIngesting = false
            }
        }
    }

    fun addPdf(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }

        val existingIndex = openPdfs.indexOf(uri)
        if (existingIndex >= 0) {
            activeIndex = existingIndex
            aiHelper.activate(uri.toString())
            return
        }

        val name = getFileName(context, uri)
        openPdfs.add(uri)
        pdfNames[uri] = name
        activeIndex = openPdfs.lastIndex
        ingestPdf(uri)
    }

    fun closePdf(index: Int) {
        if (index !in openPdfs.indices) return

        val uri = openPdfs[index]
        openPdfs.removeAt(index)
        pdfNames.remove(uri)
        aiHelper.remove(uri.toString())

        activeIndex = when {
            openPdfs.isEmpty() -> -1
            index < activeIndex -> activeIndex - 1
            index == activeIndex -> activeIndex.coerceAtMost(openPdfs.lastIndex)
            else -> activeIndex
        }

        openPdfs.getOrNull(activeIndex)?.let { aiHelper.activate(it.toString()) }
    }

    fun selectPdf(index: Int) {
        if (index !in openPdfs.indices) return
        activeIndex = index
        aiHelper.activate(openPdfs[index].toString())
    }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(::addPdf)
    }

    fun sendMessage() {
        val query = chatInput.trim()
        if (query.isEmpty() || isSending || isIngesting) return

        messages.add(ChatMessage(query, isUser = true))
        chatInput = ""
        isSending = true

        scope.launch {
            try {
                val answer = aiHelper.askTutor(
                    query = query,
                    enableWebSearch = enableWebSearch,
                )
                messages.add(ChatMessage(answer, isUser = false))
            } catch (e: Exception) {
                messages.add(
                    ChatMessage(
                        e.message ?: "Something went wrong. Please try again.",
                        isUser = false,
                    ),
                )
            } finally {
                isSending = false
            }
        }
    }

    fun onPageQuery(pageNumber: Int) {
        val uri = activeUri ?: return
        val name = pdfNames[uri] ?: activeName
        chatInput = aiHelper.pageChatSeed(name, pageNumber)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { pdfPicker.launch(pdfMimeTypes) },
                icon = {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                    )
                },
                text = { Text("Select PDF") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation(),
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            val isWide = maxWidth >= 840.dp

            if (isWide) {
                val totalWidthPx = constraints.maxWidth.toFloat()
                val clampedWidthRatio = pdfPaneWidthRatio.coerceIn(0.15f, 0.85f)

                Row(modifier = Modifier.fillMaxSize()) {
                    PdfPane(
                        openPdfs = openPdfs,
                        pdfNames = pdfNames,
                        activeIndex = activeIndex,
                        activeUri = activeUri,
                        activeName = activeName,
                        isIngesting = isIngesting,
                        error = error,
                        onSelectPdf = ::selectPdf,
                        onClosePdf = ::closePdf,
                        onPageQuery = ::onPageQuery,
                        modifier = Modifier
                            .weight(clampedWidthRatio)
                            .fillMaxSize(),
                    )
                    ResizableDivider(
                        isHorizontalSplit = true,
                        totalSizePx = totalWidthPx,
                        onDragDeltaRatio = { delta ->
                            pdfPaneWidthRatio = (pdfPaneWidthRatio + delta).coerceIn(0.15f, 0.85f)
                        },
                    )
                    ChatPanel(
                        messages = messages,
                        input = chatInput,
                        onInputChange = { chatInput = it },
                        onSend = ::sendMessage,
                        isSending = isSending,
                        isIngesting = isIngesting,
                        enableWebSearch = enableWebSearch,
                        onWebSearchChange = { enableWebSearch = it },
                        modifier = Modifier
                            .weight((1f - pdfPaneWidthRatio).coerceIn(0.15f, 0.85f))
                            .fillMaxSize(),
                    )
                }
            } else {
                val totalHeightPx = constraints.maxHeight.toFloat()
                val clampedHeightRatio = pdfPaneHeightRatio.coerceIn(0.15f, 0.85f)

                Column(modifier = Modifier.fillMaxSize()) {
                    PdfPane(
                        openPdfs = openPdfs,
                        pdfNames = pdfNames,
                        activeIndex = activeIndex,
                        activeUri = activeUri,
                        activeName = activeName,
                        isIngesting = isIngesting,
                        error = error,
                        onSelectPdf = ::selectPdf,
                        onClosePdf = ::closePdf,
                        onPageQuery = ::onPageQuery,
                        modifier = Modifier
                            .weight(clampedHeightRatio)
                            .fillMaxWidth(),
                    )
                    ResizableDivider(
                        isHorizontalSplit = false,
                        totalSizePx = totalHeightPx,
                        onDragDeltaRatio = { delta ->
                            pdfPaneHeightRatio = (pdfPaneHeightRatio + delta).coerceIn(0.15f, 0.85f)
                        },
                    )
                    ChatPanel(
                        messages = messages,
                        input = chatInput,
                        onInputChange = { chatInput = it },
                        onSend = ::sendMessage,
                        isSending = isSending,
                        isIngesting = isIngesting,
                        enableWebSearch = enableWebSearch,
                        onWebSearchChange = { enableWebSearch = it },
                        modifier = Modifier
                            .weight((1f - pdfPaneHeightRatio).coerceIn(0.15f, 0.85f))
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ResizableDivider(
    isHorizontalSplit: Boolean,
    totalSizePx: Float,
    onDragDeltaRatio: (Float) -> Unit,
) {
    Box(
        modifier = Modifier
            .then(
                if (isHorizontalSplit) {
                    Modifier
                        .fillMaxHeight()
                        .width(10.dp)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                },
            )
            .background(MaterialTheme.colorScheme.outlineVariant)
            .pointerInput(isHorizontalSplit, totalSizePx) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val deltaRatio = if (isHorizontalSplit) {
                        dragAmount.x / totalSizePx
                    } else {
                        dragAmount.y / totalSizePx
                    }
                    onDragDeltaRatio(deltaRatio)
                }
            },
    )
}

@Composable
private fun PdfPane(
    openPdfs: List<Uri>,
    pdfNames: Map<Uri, String>,
    activeIndex: Int,
    activeUri: Uri?,
    activeName: String,
    isIngesting: Boolean,
    error: String?,
    onSelectPdf: (Int) -> Unit,
    onClosePdf: (Int) -> Unit,
    onPageQuery: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = "SCHOLAR",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = activeName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (openPdfs.isNotEmpty()) {
            PdfTabRow(
                openPdfs = openPdfs,
                pdfNames = pdfNames,
                activeIndex = activeIndex,
                onSelectPdf = onSelectPdf,
                onClosePdf = onClosePdf,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
        }

        if (isIngesting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = "Analyzing document for the tutor…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
            )
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 1.dp,
        ) {
            when {
                error != null && openPdfs.isEmpty() -> {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(24.dp),
                    )
                }

                else -> PdfViewer(
                    uri = activeUri,
                    docName = activeName,
                    onPageQuery = onPageQuery,
                )
            }
        }
    }
}

@Composable
private fun PdfTabRow(
    openPdfs: List<Uri>,
    pdfNames: Map<Uri, String>,
    activeIndex: Int,
    onSelectPdf: (Int) -> Unit,
    onClosePdf: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        openPdfs.forEachIndexed { index, uri ->
            val selected = index == activeIndex
            val name = pdfNames[uri] ?: "PDF ${index + 1}"
            val chipColors = if (selected) {
                MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
            }

            Surface(
                onClick = { onSelectPdf(index) },
                shape = RoundedCornerShape(20.dp),
                color = chipColors.first,
                border = AssistChipDefaults.assistChipBorder(
                    enabled = true,
                    borderColor = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    },
                ),
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = chipColors.second,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.widthIn(max = 140.dp),
                    )
                    IconButton(
                        onClick = { onClosePdf(index) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close $name",
                            tint = chipColors.second,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatPanel(
    messages: List<ChatMessage>,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    isSending: Boolean,
    isIngesting: Boolean,
    enableWebSearch: Boolean,
    onWebSearchChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val textColors = MaterialTheme.colorScheme.onSurface
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = textColors,
        unfocusedTextColor = textColors,
        disabledTextColor = textColors.copy(alpha = 0.6f),
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        disabledContainerColor = MaterialTheme.colorScheme.surface,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    )

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = "AI Tutor",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Ask questions about the open PDF.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        text = "Select a PDF, then ask the tutor about its content.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            items(messages) { message ->
                ChatBubble(message = message)
            }

            if (isSending) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = if (enableWebSearch) "Searching and thinking…" else "Thinking…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
                placeholder = {
                    Text(
                        text = "Ask the AI tutor…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                shape = RoundedCornerShape(16.dp),
                maxLines = 4,
                enabled = !isSending && !isIngesting,
                colors = fieldColors,
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = { onWebSearchChange(!enableWebSearch) },
                enabled = !isSending && !isIngesting,
            ) {
                Icon(
                    imageVector = Icons.Default.TravelExplore,
                    contentDescription = if (enableWebSearch) {
                        "Search online enabled"
                    } else {
                        "Search online disabled"
                    },
                    tint = if (enableWebSearch) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            IconButton(
                onClick = onSend,
                enabled = input.isNotBlank() && !isSending && !isIngesting,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (enableWebSearch) {
            Text(
                text = "Search Online is on — the tutor may use live web results.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val bubbleColor = if (message.isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val textColor = if (message.isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp,
            ),
            color = bubbleColor,
            modifier = Modifier.fillMaxWidth(0.88f),
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = textColor,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun getFileName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && idx >= 0) {
            return cursor.getString(idx)
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
}
