package com.athlink.app.ui.screens.organisation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.*
import com.athlink.app.data.model.OrgField
import com.athlink.app.data.model.OrganisationDocument
import com.athlink.app.data.model.OrganisationDocumentType
import com.athlink.app.data.model.OrganisationRequirements
import com.athlink.app.data.model.PickedFile
import com.athlink.app.data.model.type
import com.athlink.app.ui.components.*
import com.athlink.app.ui.theme.AthlinkRed
import com.athlink.app.viewmodel.UploadUi

/**
 * STEP 6: documents. The checklist comes from [OrganisationRequirements.documents] for the chosen
 * type, so each organisation sees only what applies to it. Files go to PRIVATE Storage.
 */
@Composable
fun OrganisationDocumentsStep(
    ctx: StepContext,
    documents: List<OrganisationDocument>,
    uploads: List<UploadUi>,
    onPick: (requirementKey: String, OrganisationDocumentType, PickedFile, OrganisationDocument?) -> Unit,
    onRemove: (OrganisationDocument) -> Unit,
    onDismissUpload: (String) -> Unit,
    onSaveDetails: (OrganisationDocument, String, String, String, String) -> Unit
) {
    val type = ctx.draft.organisationType
    if (type == null) {
        InfoBanner("Choose your organisation type in step 1 first. The documents depend on it.")
        return
    }
    var editing by remember { mutableStateOf<OrganisationDocument?>(null) }
    val requirements = OrganisationRequirements.documents(type, ctx.draft.gstRegistered)
    val claimed = mutableSetOf<String>()

    InfoBanner(
        "Documents are stored privately and only Athlink reviewers can open them. Each one stays " +
            "\"Pending manual review\" until a reviewer checks it.",
        Icons.Default.Lock
    )
    ctx.error(OrgField.DOCUMENTS)?.let { InfoBanner(it, color = AthlinkRed) }

    requirements.forEach { req ->
        // Each uploaded document is listed under the first requirement that accepts it.
        val mine = documents.filter { d -> d.documentId !in claimed && d.type in req.acceptedTypes }
        claimed += mine.map { it.documentId }
        DocumentUploadCard(
            requirement = req,
            uploaded = mine,
            uploads = uploads.filter { it.requirementKey == req.key },
            editable = ctx.enabled,
            onPick = { t, file, replacing -> onPick(req.key, t, file, replacing) },
            onRemove = onRemove,
            onDismissUpload = onDismissUpload,
            onEditDetails = { editing = it }
        )
    }

    // Anything not matched above (e.g. extra supporting files) is still shown so it can be removed.
    val extra = documents.filter { it.documentId !in claimed }
    val other = com.athlink.app.data.model.DocumentRequirement(
        key = "other", title = "Other supporting documents (optional)",
        why = "Anything else that helps reviewers, e.g. a recent tournament circular or a recognition letter.",
        acceptedTypes = listOf(OrganisationDocumentType.OTHER_SUPPORTING_DOCUMENT), required = false
    )
    DocumentUploadCard(
        requirement = other,
        uploaded = extra,
        uploads = uploads.filter { it.requirementKey == other.key },
        editable = ctx.enabled,
        onPick = { t, file, replacing -> onPick(other.key, t, file, replacing) },
        onRemove = onRemove,
        onDismissUpload = onDismissUpload,
        onEditDetails = { editing = it }
    )

    editing?.let { doc ->
        DocumentDetailsDialog(
            document = doc,
            onDismiss = { editing = null },
            onSave = { n, a, i, e -> onSaveDetails(doc, n, a, i, e) }
        )
    }
}
