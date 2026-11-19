package com.kharchacheck.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kharchacheck.app.core.model.Attachment
import com.kharchacheck.app.data.files.FileVault
import com.kharchacheck.app.ui.AppViewModel
import com.kharchacheck.app.ui.LocalPlatform
import com.kharchacheck.app.ui.theme.Haldi
import com.kharchacheck.app.ui.theme.Ink
import com.kharchacheck.app.ui.theme.Ink2
import com.kharchacheck.app.ui.theme.Kc
import com.kharchacheck.app.ui.theme.MutedOnInk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Round profile photo, or the user's initials on haldi when there's no photo. */
@Composable
fun ProfileAvatar(vm: AppViewModel, name: String, size: Dp, onClick: (() -> Unit)? = null) {
    val version by vm.photoVersion.collectAsStateWithLifecycle()
    val bmp by produceState<ImageBitmap?>(null, version) {
        value = vm.profilePhoto()?.let { b -> withContext(Dispatchers.Default) { FileVault.decodeSampled(b, 512)?.asImageBitmap() } }
    }
    val initials = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "🙂" }
    Box(
        Modifier.size(size).clip(CircleShape).background(Haldi)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = "Profile photo" },
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) Image(b, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text(initials, color = Ink, fontSize = (size.value * 0.38f).sp, style = MaterialTheme.typography.titleLarge)
    }
}

/** Small preview tile: decoded image thumbnail, or a document tile for PDFs. */
@Composable
fun AttachmentThumb(vm: AppViewModel, a: Attachment, size: Dp, onClick: () -> Unit) {
    val bmp by produceState<ImageBitmap?>(null, a.id) {
        if (a.isImage) value = vm.attachmentBytes(a)?.let { b -> withContext(Dispatchers.Default) { FileVault.decodeSampled(b, 256)?.asImageBitmap() } }
    }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(14.dp)).background(Kc.colors.chip).clickable(onClick = onClick)
            .semantics { contentDescription = "${a.kind.label}, tap to view" },
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) Image(b, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(when { a.isPdf -> "📄"; a.isSheet -> "📊"; else -> a.kind.emoji }, fontSize = (size.value * 0.34f).sp)
            if (a.isPdf || a.isSheet) Text(if (a.isPdf) "PDF" else "CSV", style = MaterialTheme.typography.labelSmall, color = Kc.colors.muted)
        }
    }
}

private sealed interface Loaded {
    data object Loading : Loaded
    data class Image(val bmp: ImageBitmap) : Loaded
    data class Pdf(val doc: PdfDoc) : Loaded
    data class Failed(val why: String) : Loaded
}

/** Full-screen viewer: pinch-to-zoom for screenshots, rendered pages for PDFs. Nothing leaves the app unless asked. */
@Composable
fun AttachmentViewer(vm: AppViewModel, a: Attachment, onDismiss: () -> Unit, onDelete: (() -> Unit)?) {
    val ctx = LocalContext.current
    val p = LocalPlatform.current
    val scope = rememberCoroutineScope()
    val state by produceState<Loaded>(Loaded.Loading, a.id) {
        val bytes = vm.attachmentBytes(a)
        val loaded = when {
            bytes == null -> Loaded.Failed("This file couldn't be opened.")
            a.isSheet -> Loaded.Failed("📊 This is a spreadsheet / CSV statement.\n\nUse ✨ Read on the Statements screen to turn it into entries, or Open in app to view it.")
            a.isPdf -> PdfDoc.open(ctx, bytes)
            else -> withContext(Dispatchers.Default) { FileVault.decodeSampled(bytes, 2200)?.asImageBitmap() }?.let { Loaded.Image(it) }
                ?: Loaded.Failed("This image couldn't be shown.")
        }
        value = loaded
        awaitDispose { (loaded as? Loaded.Pdf)?.doc?.close() }
    }
    fun withBytes(block: (ByteArray) -> Unit) = scope.launch { vm.attachmentBytes(a)?.let(block) }
    val name = a.originalName.ifBlank { a.label.ifBlank { a.kind.label } }.let { n -> if (a.isPdf && !n.endsWith(".pdf", true)) "$n.pdf" else if (a.isImage && !n.contains('.')) (if (a.mime == "image/webp") "$n.webp" else "$n.jpg") else if (a.isSheet && !n.contains('.')) "$n.csv" else n }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Row(Modifier.fillMaxWidth().background(Ink).statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(a.label.ifBlank { a.kind.label }, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1)
                    Text("${a.kind.emoji} ${a.sizeLabel} · encrypted on this phone", style = MaterialTheme.typography.labelSmall, color = MutedOnInk)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (val s = state) {
                    Loaded.Loading -> CircularProgressIndicator(color = Haldi)
                    is Loaded.Image -> Zoomable(s.bmp)
                    is Loaded.Pdf -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(s.doc.pageCount) { i -> PdfPage(s.doc, i) }
                    }
                    is Loaded.Failed -> Text(s.why, color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(32.dp))
                }
            }
            Row(Modifier.fillMaxWidth().background(Ink).navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Open in app", Haldi, Ink) { withBytes { p.openExternally(it, a.mime, name) } }
                Pill("Save a copy", Ink2, Color.White) { withBytes { p.saveCopy(it, a.mime, name) } }
                Spacer(Modifier.weight(1f))
                if (onDelete != null) Pill("Delete", Ink2, com.kharchacheck.app.ui.theme.Coral, onClick = onDelete)
            }
        }
    }
}

@Composable
private fun PdfPage(doc: PdfDoc, index: Int) {
    val page by produceState<ImageBitmap?>(null, doc, index) { value = runCatching { doc.render(index, 1080) }.getOrNull() }
    val p = page
    if (p == null) Box(Modifier.fillMaxWidth().aspectRatio(0.707f).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Haldi)
    } else Image(p, contentDescription = "Page ${index + 1}", modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
}

@Composable
private fun Zoomable(bmp: ImageBitmap) {
    var scale by remember { mutableFloatStateOf(1f) }
    var ox by remember { mutableFloatStateOf(0f) }
    var oy by remember { mutableFloatStateOf(0f) }
    Image(
        bmp, contentDescription = null, contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale == 1f) { ox = 0f; oy = 0f } else { ox += pan.x; oy += pan.y }
                }
            }
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = ox; translationY = oy },
    )
}

/**
 * On-device PDF pages via Android's PdfRenderer, rendered lazily one page at a time to keep memory low.
 * The decrypted temp file lives in app cache only while the viewer is open, then it's deleted.
 * Password-protected statements can't be opened by PdfRenderer; the user is pointed to "Open in app".
 */
private class PdfDoc(private val file: File, private val renderer: PdfRenderer) {
    private val lock = Mutex()
    val pageCount: Int get() = renderer.pageCount

    suspend fun render(index: Int, width: Int): ImageBitmap = lock.withLock {
        withContext(Dispatchers.IO) {
            renderer.openPage(index).use { pg ->
                val h = (width.toFloat() * pg.height / pg.width).toInt().coerceIn(1, width * 3)
                val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(AColor.WHITE)
                pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp.asImageBitmap()
            }
        }
    }

    fun close() { runCatching { renderer.close() }; file.delete() }

    companion object {
        suspend fun open(ctx: Context, bytes: ByteArray): Loaded = withContext(Dispatchers.IO) {
            val tmp = File(ctx.cacheDir, "render-${System.nanoTime()}.pdf")
            try {
                tmp.writeBytes(bytes)
                // PdfRenderer owns the descriptor and closes it with the renderer.
                Loaded.Pdf(PdfDoc(tmp, PdfRenderer(ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY))))
            } catch (e: SecurityException) {
                tmp.delete()
                Loaded.Failed("🔒 This statement is password-protected (most bank statements are).\n\nTap “Open in app” to view it in your PDF reader, which will ask for the password.")
            } catch (e: Exception) {
                tmp.delete()
                Loaded.Failed("This PDF couldn't be shown here. Tap “Open in app” to use your PDF reader.")
            }
        }
    }
}
