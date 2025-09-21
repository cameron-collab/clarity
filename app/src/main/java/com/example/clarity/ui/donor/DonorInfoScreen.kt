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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.random.Random
import com.example.clarity.data.SessionStore
import com.example.clarity.ui.theme.Brand



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonorInfoScreen(
    sessionId: String,
    fundraiserId: String,
    // expanded so Nav.kt can stash extra fields for the SMS
    onNext: (donorId: String, mobileE164: String, email: String, fullName: String, dobIso: String, address: String) -> Unit,
    onBack: () -> Unit
) {
    Brand.ApplySystemBars()

    val tfColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Brand.primaryColor(),
        focusedLabelColor  = Brand.primaryColor(),
        cursorColor        = Brand.primaryColor()
    )

    val focus = LocalFocusManager.current
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val cached = SessionStore.donorForm

    var title by rememberSaveable { mutableStateOf(cached?.title) }
    var first by rememberSaveable { mutableStateOf(cached?.first ?: "") }
    var middle by rememberSaveable { mutableStateOf(cached?.middle ?: "") }
    var last by rememberSaveable { mutableStateOf(cached?.last ?: "") }

// dob as TextFieldValue with auto-dashes
    var dobText by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(cached?.dobIso.orEmpty()))
    }

    var phone by rememberSaveable { mutableStateOf(cached?.phoneRaw ?: "") }
    var email by rememberSaveable { mutableStateOf(cached?.email ?: "") }

    var addr1 by rememberSaveable { mutableStateOf(cached?.addr1 ?: "") }
    var addr2 by rememberSaveable { mutableStateOf(cached?.addr2 ?: "") }
    var city  by rememberSaveable { mutableStateOf(cached?.city  ?: "") }
    var region by rememberSaveable { mutableStateOf(cached?.region ?: "") }
    var postal by rememberSaveable { mutableStateOf(cached?.postal ?: "") }
    var country by rememberSaveable { mutableStateOf(cached?.country ?: "CA") }

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

    // ---- Double-tap autofill ----
    fun autofillFake() {
        // unique-ish email each time
        val stamp = System.currentTimeMillis() % 1000000
        val fakeEmail = "test$stamp@example.com"
        // required phone (raw); validator will convert to +1416...
        val fakePhone = "4165806454"
        // DOB > 25 (randomize between 26 and 40 years)
        val years = 26L + Random.nextLong(0, 15)
        val dob = LocalDate.now().minusYears(years).withDayOfMonth(1) // safe day
            .minusDays(Random.nextLong(0, 20)) // small variation
            .format(DateTimeFormatter.ISO_DATE)

        title = "Mr."
        first = "Alex"
        middle = "Q"
        last = "Tester"

        // ensure cursor at end with formatted text
        dobText = TextFieldValue(dob, TextRange(dob.length))

        phone = fakePhone
        email = fakeEmail

        addr1 = "123 Example St"
        addr2 = "Unit 4"
        city = "Toronto"
        region = "ON"
        postal = "M5V 2T6"
        country = "CA"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Donor Information") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                colors = Brand.appBarColors()
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(scroll)
                // 👇 double-tap anywhere to autofill
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            autofillFake()
                            focus.clearFocus()
                        }
                    )
                }
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
                    colors = tfColors,
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
                    colors = tfColors,
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
                    colors = tfColors,
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
                    colors = tfColors,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) })
                )
            }

            // DOB with auto-dash formatting + cursor control
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
                    dobText = TextFieldValue(formatted, TextRange(formatted.length))
                },
                label = { Text("Date of birth (YYYY-MM-DD)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = tfColors,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }),
                //supportingText = { Text("Type digits only; dashes are added automatically • Double-tap to autofill") }
            )

            // Contact
            OutlinedTextField(
                value = phone, onValueChange = { phone = it },
                label = { Text("Mobile phone (+1..., etc.)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                colors = tfColors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) })
            )
            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                colors = tfColors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) })
            )

            // Address
            OutlinedTextField(
                value = addr1, onValueChange = { addr1 = it },
                label = { Text("Address line 1") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = tfColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) })
            )
            OutlinedTextField(
                value = addr2, onValueChange = { addr2 = it },
                label = { Text("Address line 2 (optional)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = tfColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) })
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = city, onValueChange = { city = it },
                    label = { Text("City") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = tfColors,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) })
                )
                OutlinedTextField(
                    value = region, onValueChange = { region = it.uppercase(Locale.CANADA) },
                    label = { Text("Province/State") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = tfColors,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) })
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = postal, onValueChange = { postal = it.uppercase(Locale.CANADA) },
                    label = { Text("Postal/ZIP") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = tfColors,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) })
                )
                OutlinedTextField(
                    value = country, onValueChange = { country = it.uppercase(Locale.CANADA) },
                    label = { Text("Country") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = tfColors,
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
                            SessionStore.donorForm = com.example.clarity.data.DonorForm(
                                title = title,
                                first = first.trim(),
                                middle = middle.trim(),
                                last = last.trim(),
                                dobIso = dobText.text,
                                phoneRaw = phone.trim(),   // keep raw; you already pass e164 forward separately
                                email = email.trim(),
                                addr1 = addr1.trim(),
                                addr2 = addr2.ifBlank { "" },
                                city = city.trim(),
                                region = region.trim(),
                                postal = postal.trim(),
                                country = country.trim().ifBlank { "CA" }
                            )
                            val out = withContext(Dispatchers.IO) {
                                RetrofitProvider.api.donorUpsert(
                                    DonorUpsertIn(
                                        title = title,
                                        first_name = first.trim(),
                                        middle_name = middle.ifBlank { null },
                                        last_name = last.trim(),
                                        dob_iso = dobText.text,      // already YYYY-MM-DD
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

                            val fullName = listOfNotNull(
                                title?.takeIf { it.isNotBlank() },
                                first.trim(),
                                middle.trim().takeIf { it.isNotBlank() },
                                last.trim()
                            ).joinToString(" ")

                            val addressLine = buildString {
                                append(addr1.trim())
                                if (addr2.isNotBlank()) append(", ${addr2.trim()}")
                                append(", ${city.trim()}, ${region.trim()} ${postal.trim()}, ${country.trim()}")
                            }

                            onNext(out.donor_id, e164, email.trim(), fullName, dobText.text, addressLine)
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
                colors = Brand.buttonColors(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (loading) "Saving…" else "Continue")
            }
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
