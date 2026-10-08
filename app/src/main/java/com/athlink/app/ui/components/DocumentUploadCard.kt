package com.athlink.app.ui.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athlink.app.data.model.DocumentRequirement
import com.athlink.app.data.model.OrganisationDocument
import com.athlink.app.data.model.OrganisationDocumentType
import com.athlink.app.data.model.OrganisationValidators
import com.athlink.app.data.model.PickedFile
import com.athlink.app.data.model.reviewStatus
import com.athlink.app.data.model.type
import com.athlink.app.ui.theme.*
import com.athlink.app.viewmodel.UploadUi

/** Reads display name, MIME type and size of a picked document (no Firebase involved). */
fun readPickedFile(context: Context, uri: Uri): PickedFile {
    val resolver = context.contentResolver
    var name = "document"
    var size = -1L
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
            if (nameIdx >= 0) name = c.getString(nameIdx) ?: name
            if (sizeIdx >= 0 && !c.isNull(sizeIdx)) size = c.getLong(sizeIdx)
        }
    }
    return PickedFile(uri = uri.toString(), fileName = name, mimeType = resolver.getType(uri), sizeBytes = size)
}

/**
 * One line of the document checklist: what it is, why it's needed, the uploaded file(s) with
 * status, in-progress uploads with progress, and Upload / Replace / Remove actions.
 * Files are only uploaded when the user picks one here.
 */
@Composable
fun DocumentUploadCard(
    requirement: DocumentRequirement,
    uploaded: List<OrganisationDocument>,
    uploads: List<UploadUi>,
    editable: Boolean,
    onPick: (type: OrganisationDocumentType, file: PickedFile, replacing: OrganisationDocument?) -> Unit,
    onRemove: (OrganisationDocument) -> Unit,
    onDismissUpload: (String) -> Unit,
    onEditDetails: (OrganisationDocument) -> Unit
) {
    val context = LocalContext.current
    var chosenType by remember(requirement.key) { mutableStateOf(requirement.acceptedTypes.first()) }
    var replacing by remember { mutableStateOf<OrganisationDocument?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPick(replacing?.type ?: chosenType, readPickedFile(context, uri), replacing)
        replacing = null
    }
    val mimeTypes = OrganisationValidators.DOCUMENT_MIME_TYPES.toTypedArray()
    val satisfied = uploaded.isNotEmpty()

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (satisfied) Icons.Default.CheckCircle else Icons.Default.UploadFile, null,
                    tint = if (satisfied) AthlinkGreen else AthlinkOrange, modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(requirement.title + if (requirement.required) " *" else "", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(requirement.why, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            uploaded.forEach { doc ->
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (doc.mimeType == "application/pdf") Icons.Default.PictureAsPdf else Icons.Default.Image, null,
                            tint = AthlinkBlueLight, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(doc.fileName.ifBlank { "Document" }, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${doc.type?.label ?: doc.documentType} · ${doc.reviewStatus.label}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            doc.expiryDate?.let { Text("Expires $it", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        if (editable) {
                            IconButton(onClick = { onEditDetails(doc) }) { Icon(Icons.Default.EditNote, "Edit document details") }
                            IconButton(onClick = { replacing = doc; launcher.launch(mimeTypes) }) { Icon(Icons.Default.SwapHoriz, "Replace document") }
                            IconButton(onClick = { onRemove(doc) }) { Icon(Icons.Default.Delete, "Remove document", tint = AthlinkRed) }
                        }
                    }
                }
            }

            uploads.forEach { up ->
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(up.fileName, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onDismissUpload(up.localId) }) {
                            Icon(Icons.Default.Close, if (up.error != null) "Dismiss" else "Cancel upload")
                        }
                    }
                    if (up.error != null) {
                        Text(up.error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    } else {
                        LinearProgressIndicator(
                            progress = { up.progress }, modifier = Modifier.fillMaxWidth(),
                            color = AthlinkOrange, trackColor = AthlinkOrange.copy(0.15f)
                        )
                        Text("Uploading… ${(up.progress * 100).toInt()}%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (editable) {
                if (requirement.acceptedTypes.size > 1) {
                    DropdownField(
                        label = "Document type",
                        options = requirement.acceptedTypes,
                        selected = chosenType,
                        optionLabel = { it.label },
                        onSelect = { chosenType = it }
                    )
                }
                OutlinedButton(
                    onClick = { replacing = null; launcher.launch(mimeTypes) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AthlinkOrange)
                ) {
                    Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (satisfied) "Add another file" else "Upload file")
                }
                Text("PDF, JPG or PNG · max ${OrganisationValidators.MAX_DOCUMENT_BYTES / (1024 * 1024)} MB", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Dialog for the optional descriptive details of an uploaded document. */
@Composable
fun DocumentDetailsDialog(
    document: OrganisationDocument,
    onDismiss: () -> Unit,
    onSave: (number: String, authority: String, issued: String, expiry: String) -> Unit
) {
    var number by remember { mutableStateOf(document.documentNumber.orEmpty()) }
    var authority by remember { mutableStateOf(document.issuingAuthority.orEmpty()) }
    var issued by remember { mutableStateOf(document.issuedDate.orEmpty()) }
    var expiry by remember { mutableStateOf(document.expiryDate.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Document details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("All optional. An expiry date lets us remind you to renew before verification lapses.", fontSize = 12.sp)
                AppTextField(number, { number = it }, "Document number")
                AppTextField(authority, { authority = it }, "Issuing authority")
                AppTextField(issued, { issued = it }, "Issued (YYYY-MM-DD)")
                AppTextField(expiry, { expiry = it }, "Expires (YYYY-MM-DD)")
            }
        },
        confirmButton = { TextButton(onClick = { onSave(number, authority, issued, expiry); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
