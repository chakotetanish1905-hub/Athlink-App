package com.athlink.app.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

/**
 * Shows an organisation logo. Logos are stored as small `data:image/jpeg;base64,...` URIs on the
 * organisation document (no Cloud Storage needed); plain http(s) URLs still work via Coil.
 */
@Composable
fun OrgLogoImage(logoUrl: String, contentDescription: String, modifier: Modifier = Modifier) {
    if (logoUrl.startsWith("data:")) {
        val bitmap = remember(logoUrl) {
            runCatching {
                val bytes = Base64.decode(logoUrl.substringAfter(","), Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = contentDescription, modifier = modifier, contentScale = ContentScale.Crop)
        }
    } else {
        AsyncImage(model = logoUrl, contentDescription = contentDescription, modifier = modifier, contentScale = ContentScale.Crop)
    }
}
