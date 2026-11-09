package com.example.smartskan.ui

import androidx.compose.ui.res.stringResource
import com.example.smartskan.R
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.smartskan.data.Repository
import com.example.smartskan.data.ScannedDoc
import com.example.smartskan.data.Signature
import com.example.smartskan.pdf.AnnotationRenderer
import com.example.smartskan.pdf.InkAnnotation
import com.example.smartskan.pdf.PdfAnnotation
import com.example.smartskan.pdf.PdfPages
import com.example.smartskan.pdf.SignatureAnnotation
import com.example.smartskan.pdf.TextAnnotation
import com.example.smartskan.pdf.isMovable
import com.example.smartskan.pdf.moved
import com.example.smartskan.pdf.resized
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class Tool(@StringRes val label: Int, val icon: ImageVector) {
    SELECT(R.string.editor_select, Icons.Outlined.TouchApp),
    PEN(R.string.editor_pen, Icons.Outlined.Edit),
    HIGHLIGHT(R.string.editor_highlight, Icons.Outlined.BorderColor),
    TEXT(R.string.editor_text, Icons.Outlined.TextFields),
    SIGN(R.string.editor_sign, Icons.Outlined.Gesture),
}

private val inkColors = listOf(0xFF111111, 0xFF1A49C9, 0xFFD32F2F, 0xFF2E7D32).map { it.toInt() }
private val highlightColors = listOf(0x66FFEB3B, 0x6669F0AE, 0x66FF80AB, 0x6640C4FF).map { it.toInt() }

private data class DragTarget(val id: Long, val resize: Boolean)
private data class TextRequest(val at: Offset?, val editId: Long?, val initial: String)

/** Annotations per page, with a simple snapshot-based undo stack. */
private class EditorState {
    private val pages = mutableStateMapOf<Int, List<PdfAnnotation>>()
    private val undoStack = mutableStateListOf<Pair<Int, List<PdfAnnotation>>>()
    private var nextId = 1L
    var selectedId by mutableStateOf<Long?>(null)

    val canUndo get() = undoStack.isNotEmpty()
    val hasChanges get() = pages.values.any { it.isNotEmpty() }
    val hasSignature get() = pages.values.any { list -> list.any { it is SignatureAnnotation } }

    fun newId() = nextId++
    fun items(page: Int): List<PdfAnnotation> = pages[page].orEmpty()
    fun all(pageCount: Int) = (0 until pageCount).map { items(it) }

    fun checkpoint(page: Int) {
        undoStack.add(page to items(page))
        if (undoStack.size > 100) undoStack.removeAt(0)
    }

    fun add(page: Int, a: PdfAnnotation) {
        checkpoint(page)
        pages[page] = items(page) + a
    }

    fun update(page: Int, id: Long, transform: (PdfAnnotation) -> PdfAnnotation) {
        pages[page] = items(page).map { if (it.id == id) transform(it) else it }
    }

    fun remove(page: Int, id: Long) {
        checkpoint(page)
        pages[page] = items(page).filterNot { it.id == id }
        if (selectedId == id) selectedId = null
    }

    /** Returns the page that changed. */
    fun undo(): Int? {
        if (undoStack.isEmpty()) return null
        val (page, list) = undoStack.removeAt(undoStack.lastIndex)
        pages[page] = list
        selectedId = null
        return page
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfEditorDialog(doc: ScannedDoc, repo: Repository, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signature by repo.signature.collectAsState()

    val opened = rememberOpenedPdf(repo.pdfFile(doc))
    val pdf = opened.pages
    val loadError = opened.error

    val state = remember { EditorState() }
    var tool by remember { mutableStateOf(Tool.PEN) }
    var inkColor by remember { mutableIntStateOf(inkColors[0]) }
    var highlightColor by remember { mutableIntStateOf(highlightColors[0]) }
    var pageIndex by remember { mutableIntStateOf(0) }
    var textRequest by remember { mutableStateOf<TextRequest?>(null) }
    var showSignaturePad by remember { mutableStateOf(false) }
    var pendingSignatureAt by remember { mutableStateOf<Offset?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    fun requestClose() {
        if (state.hasChanges) confirmDiscard = true else onDismiss()
    }

    fun selectTool(t: Tool) {
        tool = t
        if (t != Tool.SELECT) state.selectedId = null
    }

    fun placeSignature(sig: Signature, at: Offset) {
        val size = pdf?.sizes?.getOrNull(pageIndex) ?: return
        var w = size.width * 0.32f
        if (w * sig.aspect > size.height * 0.15f) w = size.height * 0.15f / sig.aspect
        val a = SignatureAnnotation(state.newId(), sig, Offset(at.x - w / 2, at.y - w * sig.aspect / 2), w, inkColor)
        state.add(pageIndex, a)
        tool = Tool.SELECT
        state.selectedId = a.id
    }

    fun onTap(at: Offset, hitId: Long?) {
        when (tool) {
            Tool.SELECT -> {
                val hit = hitId?.let { id -> state.items(pageIndex).firstOrNull { it.id == id } }
                if (hit is TextAnnotation && state.selectedId == hit.id) {
                    textRequest = TextRequest(null, hit.id, hit.text)
                } else {
                    state.selectedId = hit?.id
                }
            }
            Tool.TEXT -> textRequest = TextRequest(at, null, "")
            Tool.SIGN -> {
                val sig = signature
                if (sig == null) {
                    pendingSignatureAt = at
                    showSignaturePad = true
                } else {
                    placeSignature(sig, at)
                }
            }
            Tool.PEN, Tool.HIGHLIGHT -> Unit
        }
    }

    fun save() {
        val p = pdf ?: return
        saving = true
        scope.launch {
            try {
                val name = context.getString(if (state.hasSignature) R.string.doc_signed else R.string.doc_annotated, doc.name)
                repo.createDocument(name, doc.isBooklet) { file, thumb ->
                    p.exportAnnotated(state.all(p.sizes.size), file, thumb)
                    p.sizes.size
                }
                toast(context, context.getString(R.string.saved_new_doc))
                onDismiss()
            } catch (e: Exception) {
                toast(context, context.getString(R.string.save_failed, e.localizedMessage))
            } finally {
                saving = false
            }
        }
    }

    Dialog(
        onDismissRequest = { requestClose() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = { requestClose() }) { Icon(Icons.Outlined.Close, stringResource(R.string.action_close)) } },
                    title = { Text(doc.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    actions = {
                        IconButton(onClick = { state.undo()?.let { pageIndex = it } }, enabled = state.canUndo) {
                            Icon(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.undo))
                        }
                        TextButton(onClick = { save() }, enabled = state.hasChanges && !saving) { Text(stringResource(R.string.action_save)) }
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    Tool.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tool == t,
                            onClick = { selectTool(t) },
                            icon = { Icon(t.icon, null) },
                            label = { Text(stringResource(t.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            },
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                ToolOptions(
                    tool = tool,
                    inkColor = inkColor,
                    highlightColor = highlightColor,
                    hasSignature = signature != null,
                    selected = state.selectedId?.let { id -> state.items(pageIndex).firstOrNull { it.id == id } },
                    onInkColor = { inkColor = it },
                    onHighlightColor = { highlightColor = it },
                    onRedrawSignature = { pendingSignatureAt = null; showSignaturePad = true },
                    onEditText = { a -> textRequest = TextRequest(null, a.id, a.text) },
                    onDelete = { a -> state.remove(pageIndex, a.id) },
                )
                Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                    val p = pdf
                    when {
                        loadError != null -> Text(loadError, color = MaterialTheme.colorScheme.error)
                        p == null -> CircularProgressIndicator()
                        else -> {
                            val highlighting = tool == Tool.HIGHLIGHT
                            val strokeColor = if (highlighting) highlightColor else inkColor
                            val strokeWidth = p.sizes[pageIndex].width * if (highlighting) 0.025f else 0.0045f
                            PageView(
                                pages = p,
                                index = pageIndex,
                                state = state,
                                tool = tool,
                                inkColor = strokeColor,
                                inkWidth = strokeWidth,
                                onInk = { points ->
                                    state.add(
                                        pageIndex,
                                        InkAnnotation(state.newId(), points, strokeColor, strokeWidth, highlighter = highlighting),
                                    )
                                },
                                onTap = { at, hit -> onTap(at, hit) },
                            )
                        }
                    }
                }
                val count = pdf?.sizes?.size ?: 0
                if (count > 1) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { pageIndex--; state.selectedId = null }, enabled = pageIndex > 0) {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.prev_page))
                        }
                        Text(stringResource(R.string.page_of, pageIndex + 1, count), style = MaterialTheme.typography.labelLarge)
                        IconButton(onClick = { pageIndex++; state.selectedId = null }, enabled = pageIndex < count - 1) {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.next_page))
                        }
                    }
                }
            }
        }

        textRequest?.let { req ->
            TextInputDialog(
                title = stringResource(if (req.editId == null) R.string.add_text else R.string.edit_text),
                initial = req.initial,
                confirmLabel = stringResource(if (req.editId == null) R.string.action_add else R.string.action_save),
                onConfirm = { text ->
                    if (req.editId != null) {
                        state.checkpoint(pageIndex)
                        state.update(pageIndex, req.editId) { (it as? TextAnnotation)?.copy(text = text) ?: it }
                    } else if (req.at != null) {
                        val fontSize = (pdf?.sizes?.get(pageIndex)?.width ?: 600) * 0.026f
                        val a = TextAnnotation(state.newId(), text, req.at - Offset(0f, fontSize * 0.6f), fontSize, inkColor)
                        state.add(pageIndex, a)
                        tool = Tool.SELECT
                        state.selectedId = a.id
                    }
                },
                onDismiss = { textRequest = null },
            )
        }
        if (showSignaturePad) {
            SignaturePadDialog(
                onSave = { sig ->
                    repo.saveSignature(sig)
                    pendingSignatureAt?.let { placeSignature(sig, it) }
                    pendingSignatureAt = null
                    showSignaturePad = false
                },
                onDismiss = { showSignaturePad = false; pendingSignatureAt = null },
            )
        }
        if (confirmDiscard) {
            ConfirmDialog(
                stringResource(R.string.discard_title), stringResource(R.string.discard_body), stringResource(R.string.action_discard),
                onConfirm = onDismiss, onDismiss = { confirmDiscard = false },
            )
        }
    }
}

@Composable
private fun ToolOptions(
    tool: Tool,
    inkColor: Int,
    highlightColor: Int,
    hasSignature: Boolean,
    selected: PdfAnnotation?,
    onInkColor: (Int) -> Unit,
    onHighlightColor: (Int) -> Unit,
    onRedrawSignature: () -> Unit,
    onEditText: (TextAnnotation) -> Unit,
    onDelete: (PdfAnnotation) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        @Composable
        fun hint(text: String, modifier: Modifier = Modifier) = Text(
            text, modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (tool) {
            Tool.PEN, Tool.TEXT, Tool.SIGN -> {
                inkColors.forEach { c -> ColorDot(c, c == inkColor) { onInkColor(c) } }
                Spacer(Modifier.weight(1f))
                when (tool) {
                    Tool.TEXT -> hint(stringResource(R.string.tap_add_text))
                    Tool.SIGN -> TextButton(onClick = onRedrawSignature) {
                        Text(stringResource(if (hasSignature) R.string.redraw_signature else R.string.draw_signature))
                    }
                    else -> hint(stringResource(R.string.draw_finger))
                }
            }
            Tool.HIGHLIGHT -> {
                highlightColors.forEach { c -> ColorDot(c or 0xFF000000.toInt(), c == highlightColor) { onHighlightColor(c) } }
                Spacer(Modifier.weight(1f))
                hint(stringResource(R.string.drag_over_text))
            }
            Tool.SELECT -> {
                if (selected == null) {
                    hint(stringResource(R.string.select_hint), Modifier.weight(1f))
                } else {
                    hint(stringResource(R.string.move_hint), Modifier.weight(1f))
                    if (selected is TextAnnotation) {
                        TextButton(onClick = { onEditText(selected) }) { Text(stringResource(R.string.action_edit)) }
                    }
                    IconButton(onClick = { onDelete(selected) }) {
                        Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorDot(color: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(
                if (selected) 3.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                CircleShape,
            )
            .clickable(onClick = onClick),
    )
}

@Composable
private fun PageView(
    pages: PdfPages,
    index: Int,
    state: EditorState,
    tool: Tool,
    inkColor: Int,
    inkWidth: Float,
    onInk: (List<Offset>) -> Unit,
    onTap: (at: Offset, hitId: Long?) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val size = pages.sizes[index]
        val aspect = size.width.toFloat() / size.height
        val boxW = if (maxWidth / maxHeight > aspect) maxHeight * aspect else maxWidth
        val boxH = boxW / aspect
        val density = LocalDensity.current
        val widthPx = with(density) { boxW.toPx() }
        val scale = widthPx / size.width
        val handleRadius = with(density) { 24.dp.toPx() } / scale
        val primary = MaterialTheme.colorScheme.primary

        val bitmap by produceState<ImageBitmap?>(null, index, widthPx.roundToInt()) {
            value = null
            value = pages.render(index, widthPx.roundToInt().coerceAtMost(2000))?.asImageBitmap()
        }
        var live by remember(index) { mutableStateOf<List<Offset>?>(null) }
        val currentInk by rememberUpdatedState(onInk)
        val currentTap by rememberUpdatedState(onTap)
        val currentColor by rememberUpdatedState(inkColor)
        val currentWidth by rememberUpdatedState(inkWidth)

        fun hitTest(pt: Offset): DragTarget? {
            val items = state.items(index)
            state.selectedId?.let { id ->
                items.firstOrNull { it.id == id }?.let { a ->
                    if ((pt - AnnotationRenderer.bounds(a).bottomRight).getDistance() <= handleRadius) return DragTarget(id, resize = true)
                }
            }
            val hit = items.lastOrNull { it.isMovable() && AnnotationRenderer.bounds(it).inflate(handleRadius / 2).contains(pt) }
            return hit?.let { DragTarget(it.id, resize = false) }
        }

        Box(Modifier.size(boxW, boxH).shadow(6.dp).background(Color.White)) {
            bitmap?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
                ?: CircularProgressIndicator(Modifier.align(Alignment.Center))

            Canvas(
                Modifier.fillMaxSize().pointerInput(tool, index, scale) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val start = down.position / scale
                        if (tool == Tool.PEN || tool == Tool.HIGHLIGHT) {
                            down.consume()
                            val points = mutableListOf(start)
                            live = points.toList()
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val p = change.position / scale
                                if ((p - points.last()).getDistance() * scale > 1.5f) {
                                    points += p
                                    live = points.toList()
                                }
                                change.consume()
                            }
                            live = null
                            currentInk(points)
                        } else {
                            val target = if (tool == Tool.SELECT) hitTest(start) else null
                            var moved = false
                            var last = down.position
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                if (!moved && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                    moved = true
                                    if (target != null) {
                                        state.checkpoint(index)
                                        state.selectedId = target.id
                                    }
                                }
                                if (moved && target != null) {
                                    val delta = (change.position - last) / scale
                                    state.update(index, target.id) { if (target.resize) it.resized(delta.x) else it.moved(delta) }
                                    change.consume()
                                }
                                last = change.position
                            }
                            if (!moved) currentTap(start, target?.id)
                        }
                    }
                },
            ) {
                val items = state.items(index)
                drawIntoCanvas { c ->
                    AnnotationRenderer.draw(c.nativeCanvas, items, scale)
                    live?.let { pts ->
                        AnnotationRenderer.draw(
                            c.nativeCanvas,
                            InkAnnotation(-1, pts, currentColor, currentWidth, highlighter = tool == Tool.HIGHLIGHT),
                            scale,
                        )
                    }
                }
                val selected = state.selectedId?.let { id -> items.firstOrNull { it.id == id } }
                if (selected != null) {
                    val b = AnnotationRenderer.bounds(selected)
                    val r = Rect(b.left * scale, b.top * scale, b.right * scale, b.bottom * scale).inflate(4.dp.toPx())
                    drawRect(
                        primary, r.topLeft, r.size,
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))),
                    )
                    drawCircle(primary, 9.dp.toPx(), r.bottomRight)
                    drawCircle(Color.White, 4.dp.toPx(), r.bottomRight)
                }
            }
        }
    }
}

@Composable
private fun SignaturePadDialog(onSave: (Signature) -> Unit, onDismiss: () -> Unit) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var live by remember { mutableStateOf<List<Offset>?>(null) }
    val strokePx = with(LocalDensity.current) { 3.dp.toPx() }
    val ink = Color(0xFF111111)

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 6.dp) {
            Column(Modifier.padding(20.dp)) {
                Text(stringResource(R.string.sig_pad_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.sig_pad_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                ) {
                    if (strokes.isEmpty() && live == null) {
                        Text(stringResource(R.string.sign_here), Modifier.align(Alignment.Center), color = Color(0xFF9E9E9E))
                    }
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                down.consume()
                                val points = mutableListOf(down.position)
                                live = points.toList()
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    points += change.position
                                    live = points.toList()
                                    change.consume()
                                }
                                live = null
                                strokes.add(points.toList())
                            }
                        },
                    ) {
                        val y = size.height * 0.75f
                        drawLine(Color(0xFFD0D0D0), Offset(24.dp.toPx(), y), Offset(size.width - 24.dp.toPx(), y), 1.dp.toPx())
                        (strokes + listOfNotNull(live)).forEach { pts ->
                            drawPath(
                                AnnotationRenderer.smoothPath(pts).asComposePath(), ink,
                                style = Stroke(strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round),
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { strokes.clear() }, enabled = strokes.isNotEmpty()) { Text(stringResource(R.string.action_clear)) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = { onSave(normalize(strokes, strokePx)) }, enabled = strokes.isNotEmpty()) {
                        Text(stringResource(R.string.action_use))
                    }
                }
            }
        }
    }
}

/** Crops to the drawn area and scales so the signature is 1.0 wide. */
private fun normalize(strokes: List<List<Offset>>, strokePx: Float): Signature {
    val all = strokes.flatten()
    val minX = all.minOf { it.x } - strokePx
    val minY = all.minOf { it.y } - strokePx
    val w = (all.maxOf { it.x } + strokePx - minX).coerceAtLeast(1f)
    val h = (all.maxOf { it.y } + strokePx - minY).coerceAtLeast(1f)
    val normalized = strokes.map { s ->
        FloatArray(s.size * 2) { i -> if (i % 2 == 0) (s[i / 2].x - minX) / w else (s[i / 2].y - minY) / w }
    }
    return Signature(normalized, h / w, strokePx / w)
}
