package com.example.smartskan.ui

import androidx.compose.ui.res.stringResource
import com.example.smartskan.R
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NavigateBefore
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.example.smartskan.data.Repository
import com.example.smartskan.data.ScannedDoc
import com.example.smartskan.pdf.BookLayout
import com.example.smartskan.pdf.PageSize
import com.example.smartskan.pdf.PdfPages
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TURN_MILLIS = 550
private const val MAX_ZOOM = 4f

/** Which edge of a page is bound into the book; the page swings around it. */
private enum class Hinge(val originX: Float) { LEFT(0f), RIGHT(1f) }

/** Reads a PDF like a book: page turns in portrait, facing pages in landscape. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookReaderDialog(doc: ScannedDoc, repo: Repository, onDismiss: () -> Unit) {
    val opened = rememberOpenedPdf(repo.pdfFile(doc))

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(doc.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, stringResource(R.string.action_close)) } },
                )
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                val book = opened.pages
                when {
                    opened.error != null -> Text(
                        stringResource(R.string.booklet_open_failed, opened.error),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp),
                    )
                    book == null -> CircularProgressIndicator()
                    book.sizes.isEmpty() -> Text(stringResource(R.string.booklet_no_pages))
                    else -> Book(book)
                }
            }
        }
    }
}

@Composable
private fun Book(pages: PdfPages) {
    val pageCount = pages.sizes.size
    // In PDF pages rather than pager items, so the place survives switching layouts on rotation.
    var place by remember { mutableIntStateOf(0) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val facingPages = maxWidth > maxHeight && pageCount > 1
        val pager = remember(facingPages) {
            PagerState(currentPage = if (facingPages) BookLayout.spreadOfPage(place) else place) {
                if (facingPages) BookLayout.spreadCount(pageCount) else pageCount
            }
        }
        var zoom by remember { mutableFloatStateOf(1f) }
        var pan by remember { mutableStateOf(Offset.Zero) }

        LaunchedEffect(pager) {
            snapshotFlow { pager.settledPage }.collect { item ->
                place = if (facingPages) BookLayout.firstPageOfSpread(item) else item
                zoom = 1f
                pan = Offset.Zero
            }
        }

        Column(Modifier.fillMaxSize()) {
            val scope = rememberCoroutineScope()
            fun turnTo(item: Int) {
                if (item in 0 until pager.pageCount) {
                    scope.launch { pager.animateScrollToPage(item, animationSpec = tween(TURN_MILLIS)) }
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .pointerInput(Unit) {
                        // Seen before the pager: two fingers zoom, and one finger pans once zoomed in.
                        // A single finger at normal size is left alone, so it turns the page.
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.size > 1 || zoom > 1f) {
                                    zoom = (zoom * event.calculateZoom()).coerceIn(1f, MAX_ZOOM)
                                    val limitX = size.width * (zoom - 1f) / 2f
                                    val limitY = size.height * (zoom - 1f) / 2f
                                    val moved = pan + event.calculatePan()
                                    pan = Offset(moved.x.coerceIn(-limitX, limitX), moved.y.coerceIn(-limitY, limitY))
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .pointerInput(pager) {
                        detectTapGestures { tap ->
                            if (zoom > 1f) return@detectTapGestures
                            when {
                                tap.x < size.width * 0.25f -> turnTo(pager.currentPage - 1)
                                tap.x > size.width * 0.75f -> turnTo(pager.currentPage + 1)
                            }
                        }
                    }
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = pan.x
                        translationY = pan.y
                    },
            ) {
                // The page geometry below is physical, so it must not mirror in right-to-left locales.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1,
                        userScrollEnabled = zoom == 1f,
                    ) { item ->
                        if (facingPages) Spread(pages, pager, item) else SinglePage(pages, pager, item)
                    }
                }
            }
            BookControls(
                pager = pager,
                label = if (facingPages) {
                    val spread = listOfNotNull(
                        BookLayout.leftPage(pager.currentPage),
                        BookLayout.rightPage(pager.currentPage, pageCount),
                    ).joinToString("–") { "${it + 1}" }
                    stringResource(R.string.of_count, spread, pageCount)
                } else {
                    stringResource(R.string.of_count, "${pager.currentPage + 1}", pageCount)
                },
                onTurn = ::turnTo,
                onJump = { item -> scope.launch { pager.scrollToPage(item) } },
            )
        }
    }
}

/** How far the book has moved past [item]; see [BookLayout]. */
private fun PagerState.turnOf(item: Int): Float = (currentPage - item) + currentPageOffsetFraction

/** One page at a time: earlier pages lie on top of later ones and swing away to uncover them. */
@Composable
private fun SinglePage(pages: PdfPages, pager: PagerState, item: Int) {
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(-item.toFloat())
            .graphicsLayer { translationX = pager.turnOf(item) * size.width }
            .padding(12.dp),
    ) {
        PageSheet(pages, item, Hinge.LEFT, Alignment.Center) { BookLayout.singlePageRotation(pager.turnOf(item)) }
    }
}

/**
 * Facing pages. A turn hands over at its halfway point: the old right page lifts off the book, then
 * the new left page settles onto it. Whichever spread is closest to open is drawn on top.
 */
@Composable
private fun Spread(pages: PdfPages, pager: PagerState, item: Int) {
    val onTop by remember(pager, item) { derivedStateOf { abs(pager.turnOf(item)) < 0.5f } }
    val pageCount = pages.sizes.size
    Row(
        Modifier
            .fillMaxSize()
            .zIndex(if (onTop) 1f else 0f)
            .graphicsLayer { translationX = pager.turnOf(item) * size.width }
            .padding(12.dp),
    ) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            BookLayout.leftPage(item)?.let { page ->
                PageSheet(pages, page, Hinge.RIGHT, Alignment.CenterEnd) { BookLayout.leftPageRotation(pager.turnOf(item)) }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            BookLayout.rightPage(item, pageCount)?.let { page ->
                PageSheet(pages, page, Hinge.LEFT, Alignment.CenterStart) { BookLayout.rightPageRotation(pager.turnOf(item)) }
            }
        }
    }
}

/**
 * A page fitted into the available space. [rotation] is read at draw time so a turn only updates
 * the graphics layer; it returns null when the page shouldn't be visible.
 */
@Composable
private fun PageSheet(pages: PdfPages, index: Int, hinge: Hinge, alignment: Alignment, rotation: () -> Float?) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = alignment) {
        val size: PageSize = pages.sizes[index]
        val aspect = size.width.toFloat() / size.height
        val sheetWidth = if (maxWidth / maxHeight > aspect) maxHeight * aspect else maxWidth
        // Rendered above screen size so text stays sharp when zoomed in.
        val renderWidth = with(LocalDensity.current) { (sheetWidth.toPx() * 1.5f).roundToInt() }.coerceIn(1, 2000)
        val bitmap by produceState<ImageBitmap?>(null, index, renderWidth) {
            value = pages.render(index, renderWidth)?.asImageBitmap()
        }
        val spineShadow = with(LocalDensity.current) { 18.dp.toPx() }

        Box(
            Modifier
                .size(sheetWidth, sheetWidth / aspect)
                .graphicsLayer {
                    val degrees = rotation()
                    alpha = if (degrees == null) 0f else 1f
                    rotationY = degrees ?: 0f
                    transformOrigin = TransformOrigin(hinge.originX, 0.5f)
                    cameraDistance = 12f * density
                }
                .background(Color.White)
                .drawWithContent {
                    drawContent()
                    // The page curves into the binding...
                    val left = hinge == Hinge.LEFT
                    val startX = if (left) 0f else this.size.width - spineShadow
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = if (left) {
                                listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent)
                            } else {
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.18f))
                            },
                            startX = startX,
                            endX = startX + spineShadow,
                        ),
                        topLeft = Offset(startX, 0f),
                        size = Size(spineShadow, this.size.height),
                    )
                    // ...and darkens as it stands up away from the light.
                    val shade = abs(rotation() ?: 0f) / 90f * 0.35f
                    if (shade > 0f) drawRect(Color.Black.copy(alpha = shade))
                },
            contentAlignment = Alignment.Center,
        ) {
            bitmap?.let {
                Image(it, stringResource(R.string.page_n, index + 1), Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            } ?: CircularProgressIndicator()
        }
    }
}

@Composable
private fun BookControls(pager: PagerState, label: String, onTurn: (Int) -> Unit, onJump: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onTurn(pager.currentPage - 1) }, enabled = pager.currentPage > 0) {
            Icon(Icons.AutoMirrored.Outlined.NavigateBefore, stringResource(R.string.prev_page))
        }
        if (pager.pageCount > 1) {
            Slider(
                value = pager.currentPage.toFloat(),
                onValueChange = { onJump(it.roundToInt()) },
                valueRange = 0f..(pager.pageCount - 1).toFloat(),
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
        } else {
            Box(Modifier.weight(1f))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp))
        IconButton(onClick = { onTurn(pager.currentPage + 1) }, enabled = pager.currentPage < pager.pageCount - 1) {
            Icon(Icons.AutoMirrored.Outlined.NavigateNext, stringResource(R.string.next_page))
        }
    }
}
