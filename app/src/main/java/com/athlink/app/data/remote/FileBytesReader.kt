package com.athlink.app.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.athlink.app.data.model.DocumentChunks
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads picked files into memory and shrinks photos so they fit the Firestore-backed storage
 * (see [DocumentChunks]). PDFs are stored as-is.
 */
@Singleton
class FileBytesReader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class Prepared(val bytes: ByteArray, val mimeType: String)

    /** Reads [uri]; JPEG/PNG photos over the limit are downscaled to JPEG. */
    suspend fun prepareDocument(uri: String, mimeType: String?): Prepared = withContext(Dispatchers.IO) {
        val raw = readAll(uri)
        when {
            mimeType == "image/jpeg" || mimeType == "image/png" ->
                if (raw.size <= DocumentChunks.MAX_STORED_BYTES) Prepared(raw, mimeType)
                else Prepared(compressJpeg(raw, maxDimension = 2200, targetBytes = DocumentChunks.MAX_STORED_BYTES), "image/jpeg")
            else -> Prepared(raw, mimeType ?: "application/octet-stream")
        }
    }

    /**
     * Small square-ish logo as a `data:image/jpeg;base64,...` URI (about 20-60 KB), stored directly
     * on the public organisation document.
     */
    suspend fun prepareLogoDataUri(uri: String): String = withContext(Dispatchers.IO) {
        val jpeg = compressJpeg(readAll(uri), maxDimension = 320, targetBytes = 80L * 1024)
        "data:image/jpeg;base64," + Base64.encodeToString(jpeg, Base64.NO_WRAP)
    }

    private fun readAll(uri: String): ByteArray =
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
            ?: throw IllegalStateException("Couldn't open the selected file")

    /** Decodes, scales the longest side down to [maxDimension], then lowers quality until under [targetBytes]. */
    private fun compressJpeg(bytes: ByteArray, maxDimension: Int, targetBytes: Long): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDimension) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IllegalArgumentException("This image couldn't be read")
        val scale = maxDimension.toFloat() / maxOf(decoded.width, decoded.height)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true)
        } else decoded
        var quality = 88
        while (true) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            if (out.size() <= targetBytes || quality <= 40) return out.toByteArray()
            quality -= 12
        }
    }
}
