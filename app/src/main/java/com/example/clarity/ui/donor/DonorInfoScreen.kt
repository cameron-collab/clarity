package com.example.clarity.ui.donor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.unit.dp
import com.example.clarity.api.RetrofitProvider
import com.example.clarity.api.model.DonorUpsertIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.runtime.saveable.rememberSaveable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonorInfoScreen(
    sessionId: String,
    fundraiserId: String,
    onNext: (donorId: String, mobileE164: String) -> Unit,
    onBack: () -> Unit
) {
    val focus = LocalFocusManager.current
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    var title by rememberSaveable { mutableStateOf<String?>(null) }

    var first by rememberSaveable { mutableStateOf("") }
    var middle by rememberSaveable { mutableStateOf("") }
    var last by rememberSaveable { mutableStateOf("") }

    // Display text for DOB with auto-dashes. We’ll submit dobIso (same string) to backend.
    //var dobText by remember { mutableStateOf("") }
    var dobText by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }

    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }

    var addr1 by rememberSaveable { mutableStateOf("") }
    var addr2 by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var region by rememberSaveable { mutableStateOf("") }
    var postal by rememberSaveable { mutableStateOf("") }
    var country by rememberSaveable { mutableStateOf("CA") }

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun validate(): String? {
        if (first.isBlank()) return "First name is required"
        if (last.isBlank()) return "Last name is required"
        if (!isValidIsoDate(dobText.text)) return "DOB must be YYYY-MM-DD"
        if (!email.matches(Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$"""))) return "Valid email is required"
        if (phoneE164OrNull(phone) == null) return "Valid mobile phone (E.164) is required"
        if (addr1.isBlank() || city.isBlank() || region.isBlank() || postal.isBlank()) return "Full address is required"
        return null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Donor Information") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .imePadding()                 // ← keeps inputs above keyboard
                .verticalScroll(scroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Name row (all on one line)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title ?: "",
                    onValueChange = { s -> title = s.ifBlank { null } },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) })
                )

                OutlinedTextField(
                    value = first, onValueChange = { first = it },
                    label = { Text("First") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) })
                )
                OutlinedTextField(
                    value = middle, onValueChange = { middle = it },
                    label = { Text("Middle") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) })
                )
                OutlinedTextField(
                    value = last, onValueChange = { last = it },
                    label = { Text("Last") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) })
                )
            }

            // DOB with auto-dash formatting

            OutlinedTextField(
                value = dobText,
                onValueChange = { incoming ->
                    val digits = incoming.text.filter { it.isDigit() }.take(8)
                    val formatted = buildString {
                        for (i in digits.indices) {
                            append(digits[i])
                            if (i == 3 || i == 5) append('-') // YYYY- MM-
                        }
                    }
                    dobText = TextFieldValue(text = formatted, selection = TextRange(formatted.length))
                },
                label = { Text("Date of birth (YYYY-MM-DD)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }),
                supportingText = { Text("Type digits only; dashes are added automatically") }
            )



            // Contact
            OutlinedTextField(
                value = phone, onValueChange = { phone = it },
                label = { Text("Mobile phone (+1..., etc.)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) })
            )
            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) })
            )

            // Address
            OutlinedTextField(
                value = addr1, onValueChange = { addr1 = it },
                label = { Text("Address line 1") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) })
            )
            OutlinedTextField(
                value = addr2, onValueChange = { addr2 = it },
                label = { Text("Address line 2 (optional)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) })
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = city, onValueChange = { city = it },
                    label = { Text("City") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) })
                )
                OutlinedTextField(
                    value = region, onValueChange = { region = it.uppercase(Locale.CANADA) },
                    label = { Text("Province/State") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) })
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = postal, onValueChange = { postal = it.uppercase(Locale.CANADA) },
                    label = { Text("Postal/ZIP") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) })
                )
                OutlinedTextField(
                    value = country, onValueChange = { country = it.uppercase(Locale.CANADA) },
                    label = { Text("Country") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                    // Last field: show Done and collapse keyboard
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() })
                )
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    focus.clearFocus()
                    val v = validate()
                    if (v != null) { error = v; return@Button }
                    error = null
                    loading = true

                    scope.launch {
                        try {
                            val e164 = phoneE164OrNull(phone)!!
                            val out = withContext(Dispatchers.IO) {
                                RetrofitProvider.api.donorUpsert(
                                    DonorUpsertIn(
                                        title = title,
                                        first_name = first.trim(),
                                        middle_name = middle.ifBlank { null },
                                        last_name = last.trim(),
                                        dob_iso = dobText.text,               // already YYYY-MM-DD
                                        mobile_e164 = e164,
                                        email = email.trim(),
                                        address1 = addr1.trim(),
                                        address2 = addr2.ifBlank { null },
                                        city = city.trim(),
                                        region = region.trim(),
                                        postal_code = postal.trim(),
                                        country = country.trim(),
                                        fundraiser_id = fundraiserId,
                                        session_id = sessionId
                                    )
                                )
                            }
                            onNext(out.donor_id, e164)
                        } catch (ex: retrofit2.HttpException) {
                            error = try { ex.response()?.errorBody()?.string() ?: ex.message() }
                            catch (_: Exception) { ex.message() }
                        } catch (ex: Exception) {
                            error = ex.message
                        } finally {
                            loading = false
                        }
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (loading) "Saving…" else "Continue") }
        }
    }
}

/** Helpers **/

private fun phoneE164OrNull(raw: String): String? {
    val trimmed = raw.trim().replace("""[^\d+]""".toRegex(), "")
    if (trimmed.startsWith("+") && trimmed.length in 11..16) return trimmed
    val digits = trimmed.filter { it.isDigit() }
    return when (digits.length) {
        10 -> "+1$digits"
        11 -> if (digits.startsWith("1")) "+$digits" else null
        else -> null
    }
}

private fun isValidIsoDate(s: String): Boolean =
    runCatching { LocalDate.parse(s, DateTimeFormatter.ISO_DATE) }.isSuccess

/** Auto-dash YYYY-MM-DD as the user types digits */
private fun formatDobInput(raw: String): String {
    val digits = raw.filter { it.isDigit() }.take(8)
    val sb = StringBuilder()
    for (i in digits.indices) {
        sb.append(digits[i])
        if (i == 3 || i == 5) sb.append('-')  // after YYYY and MM
    }
    return sb.toString()
}
