package com.cashmemer.ui.ai

import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.union
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextField
import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cashmemer.R
import com.cashmemer.core.data.CashMemerRepository
import com.cashmemer.core.model.Receipt
import com.cashmemer.core.network.GeminiChat
import com.cashmemer.core.util.Format
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ChatMessage(val fromUser: Boolean, val text: String)

private const val SYSTEM_PROMPT =
    "You are the assistant inside Cash Memer, a receipt app. Answer questions about the " +
        "user's receipts and exchange rates by calling the tools; never invent figures. " +
        "Dates are yyyy-MM-dd. Keep answers short and clear."

/** Asks Gemini a question. Gemini can call read-only tools that read the app's data. */
class AskAiViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CashMemerRepository.get(application)
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** The conversation as Gemini sees it. Kept only while this screen is open. */
    private val history = JSONArray()

    fun ask(question: String) {
        val text = question.trim()
        if (text.isEmpty() || _busy.value) return
        _messages.update { it + ChatMessage(fromUser = true, text = text) }
        history.put(JSONObject().put("role", "user")
            .put("parts", JSONArray().put(JSONObject().put("text", text))))
        _busy.value = true
        viewModelScope.launch {
            val reply = runConversation()
            _messages.update { it + ChatMessage(fromUser = false, text = reply) }
            _busy.value = false
        }
    }

    private suspend fun runConversation(): String {
        val failed = getApplication<Application>().getString(R.string.ai_error)
        repeat(6) {
            val body = JSONObject()
                .put("system_instruction", JSONObject().put("parts",
                    JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT))))
                .put("contents", history)
                .put("tools", JSONArray().put(JSONObject().put("functionDeclarations", declarations())))
            val response = GeminiChat.generate(body).getOrNull() ?: return failed
            val parts = response.optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts") ?: return failed
            history.put(JSONObject().put("role", "model").put("parts", parts))

            val calls = (0 until parts.length()).mapNotNull { parts.getJSONObject(it).optJSONObject("functionCall") }
            if (calls.isEmpty()) {
                val answer = (0 until parts.length()).joinToString("") { parts.getJSONObject(it).optString("text") }
                return answer.ifBlank { failed }
            }
            val responses = JSONArray()
            calls.forEach { call ->
                val name = call.optString("name")
                val output = runTool(name, call.optJSONObject("args") ?: JSONObject())
                responses.put(JSONObject().put("functionResponse", JSONObject()
                    .put("name", name)
                    .put("response", JSONObject().put("result", output))))
            }
            history.put(JSONObject().put("role", "user").put("parts", responses))
        }
        return failed
    }

    private suspend fun runTool(name: String, args: JSONObject): String = withContext(Dispatchers.IO) {
        when (name) {
            "get_exchange_rates" -> ratesJson()
            "search_receipts" -> JSONArray(matching(args).take(30).map { summary(it) }).toString()
            "total_spending" -> totalsJson(matching(args))
            else -> "{\"error\":\"unknown tool\"}"
        }
    }

    private suspend fun ratesJson(): String {
        val rates = repository.observeRates().first()
        return JSONArray(rates.map {
            JSONObject().put("code", it.code).put("name", it.displayName)
                .put("rate", it.rate).put("updated", Format.date(it.updatedAt))
        }).toString()
    }

    private suspend fun matching(args: JSONObject): List<Receipt> {
        val zone = ZoneId.systemDefault()
        val from = parseDate(args.optString("from"))?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: Long.MIN_VALUE
        val to = parseDate(args.optString("to"))?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
            ?.minus(1) ?: Long.MAX_VALUE
        val store = args.optString("store").trim()
        val category = args.optString("category").trim()
        val payment = args.optString("payment").trim()
        return repository.allReceiptsOnce().filter { r ->
            r.createdAt in from..to &&
                (store.isEmpty() || r.placeName.contains(store, ignoreCase = true)) &&
                (category.isEmpty() || r.category.contains(category, ignoreCase = true)) &&
                (payment.isEmpty() || r.paymentType.contains(payment, ignoreCase = true))
        }.sortedByDescending { it.createdAt }
    }

    private fun summary(r: Receipt): JSONObject = JSONObject()
        .put("date", Format.date(r.createdAt))
        .put("store", r.placeName)
        .put("total", Format.amountWithCurrency(r.total, r.currencyCode))
        .put("category", r.category)
        .put("payment", r.paymentType)

    private fun totalsJson(receipts: List<Receipt>): String {
        val perCurrency = receipts.groupBy { it.currencyCode }.mapValues { (_, list) -> list.sumOf { it.total } }
        return JSONObject()
            .put("receipts", receipts.size)
            .put("totals", JSONArray(perCurrency.map { (code, sum) ->
                JSONObject().put("currency", code).put("total", Format.amountWithCurrency(sum, code))
            }))
            .toString()
    }

    private fun parseDate(text: String): LocalDate? =
        runCatching { LocalDate.parse(text.trim()) }.getOrNull()

    private fun declarations(): JSONArray = JSONArray()
        .put(declaration("get_exchange_rates", "The exchange rates the app holds.",
            JSONObject().put("type", "OBJECT").put("properties", JSONObject())))
        .put(declaration("search_receipts",
            "Find past receipts, newest first. All filters are optional.", filters()))
        .put(declaration("total_spending",
            "Total spent on receipts matching the same optional filters, per currency.", filters()))

    private fun declaration(name: String, description: String, parameters: JSONObject): JSONObject =
        JSONObject().put("name", name).put("description", description).put("parameters", parameters)

    private fun filters(): JSONObject = JSONObject().put("type", "OBJECT").put("properties", JSONObject()
        .put("from", text("Start date, yyyy-MM-dd"))
        .put("to", text("End date, yyyy-MM-dd"))
        .put("store", text("Part of the store name"))
        .put("category", text("Category, for example Groceries or Food"))
        .put("payment", text("Payment method, for example Card or Cash")))

    private fun text(description: String): JSONObject =
        JSONObject().put("type", "STRING").put("description", description)
}

/** The chat screen: a header, example questions when empty, the conversation, and an input bar. */
@Composable
fun AskAiScreen(onBack: () -> Unit, viewModel: AskAiViewModel = viewModel()) {
    val messages by viewModel.messages.collectAsState()
    val busy by viewModel.busy.collectAsState()
    var draft by remember { mutableStateOf("") }
    val suggestions = listOf(
        stringResource(R.string.ai_suggest_1),
        stringResource(R.string.ai_suggest_2),
        stringResource(R.string.ai_suggest_3),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        ChatHeader(onBack = onBack)

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            if (messages.isEmpty()) {
                item { EmptyState(suggestions = suggestions, onSuggestion = { viewModel.ask(it) }) }
            }
            items(messages) { message -> ChatBubble(message) }
            if (busy) {
                item { ThinkingBubble() }
            }
        }

        InputBar(
            value = draft,
            onValueChange = { draft = it },
            enabled = !busy,
            onSend = {
                viewModel.ask(draft)
                draft = ""
            },
        )
    }
}

@Composable
private fun ChatHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.bulk_back))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.ai_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.ai_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun EmptyState(suggestions: List<String>, onSuggestion: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(64.dp),
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(16.dp),
            )
        }
        Text(
            stringResource(R.string.ai_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.ai_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestions.forEach { text ->
                Surface(
                    onClick = { onSuggestion(text) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val shape = if (message.fromUser) {
        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = shape,
            color = if (message.fromUser) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (message.fromUser) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(
                message.text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ThinkingBubble() {
    Surface(
        shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            stringResource(R.string.ai_thinking),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onSend: () -> Unit,
) {
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            // One padding for the keyboard or the navigation bar, whichever is taller.
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = {
                    Text(
                        stringResource(R.string.ai_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                shape = RoundedCornerShape(28.dp),
                maxLines = 4,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(28.dp)),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
            )
            val canSend = enabled && value.isNotBlank()
            FilledIconButton(
                onClick = onSend,
                enabled = canSend,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(Icons.Filled.Send, contentDescription = stringResource(R.string.ai_send))
            }
        }
    }
}

/** The small circle AI button shown in the bottom-right corner. */
@Composable
fun AskAiFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    SmallFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Icon(Icons.Filled.AutoAwesome, contentDescription = stringResource(R.string.ai_title))
    }
}
