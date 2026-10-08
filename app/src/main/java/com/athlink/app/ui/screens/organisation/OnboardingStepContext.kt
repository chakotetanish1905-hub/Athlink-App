package com.athlink.app.ui.screens.organisation

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.athlink.app.data.model.OrgField
import com.athlink.app.data.model.OrganisationDraft
import com.athlink.app.ui.components.AppTextField
import com.athlink.app.ui.components.fieldLabel

/**
 * What every onboarding step needs: the draft, its errors, whether editing is allowed, and a
 * single update function (which also clears the edited field's error). Steps never touch
 * Firebase; the ViewModel saves the draft when the user taps Next / Save draft.
 */
class StepContext(
    val draft: OrganisationDraft,
    val errors: Map<OrgField, String>,
    val enabled: Boolean,
    val update: (OrgField?, (OrganisationDraft) -> OrganisationDraft) -> Unit
) {
    fun error(field: OrgField): String? = errors[field]
}

enum class InputKind { TEXT, WORDS, SENTENCES, EMAIL, PHONE, NUMBER, URL, CAPS }

/** A text field bound to one draft property. */
@Composable
fun StepContext.Input(
    value: String,
    label: String,
    field: OrgField,
    required: Boolean = true,
    icon: ImageVector? = null,
    kind: InputKind = InputKind.WORDS,
    multiline: Boolean = false,
    hint: String? = null,
    modifier: Modifier = Modifier,
    maxLength: Int = 300,
    set: (OrganisationDraft, String) -> OrganisationDraft
) {
    val keyboard = when (kind) {
        InputKind.TEXT -> KeyboardOptions(imeAction = ImeAction.Next)
        InputKind.WORDS -> KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
        InputKind.SENTENCES -> KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        InputKind.EMAIL -> KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
        InputKind.PHONE -> KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next)
        InputKind.NUMBER -> KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
        InputKind.URL -> KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next)
        InputKind.CAPS -> KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next)
    }
    AppTextField(
        value = value,
        onValueChange = { v -> if (v.length <= maxLength) update(field) { d -> set(d, v) } },
        label = fieldLabel(label, required),
        modifier = modifier,
        leadingIcon = icon,
        keyboardOptions = keyboard,
        isError = error(field) != null,
        errorMessage = error(field).orEmpty(),
        singleLine = !multiline,
        maxLines = if (multiline) 5 else 1,
        enabled = enabled,
        supportingText = hint
    )
}
