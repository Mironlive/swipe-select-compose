package io.github.mironlive.swipeselect.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mironlive.swipeselect.ActivationWhen
import io.github.mironlive.swipeselect.EdgeSide
import io.github.mironlive.swipeselect.SwipeSelectActivation
import io.github.mironlive.swipeselect.SwipeSelectState
import io.github.mironlive.swipeselect.rememberSwipeSelectState
import io.github.mironlive.swipeselect.swipeSelect

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialTab = intent.getIntExtra("tab", 0)
        setContent { MaterialTheme { App(initialTab) } }
    }
}

private val tabs = listOf("Mail", "Photos", "Chat", "Horizontal")

@Composable
private fun App(initialTab: Int) {
    var tab by remember { mutableIntStateOf(initialTab) }
    Scaffold { padding ->
        Column(Modifier.padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
            }
            when (tab) {
                0 -> MailDemo()
                1 -> GridDemo()
                2 -> ChatDemo()
                else -> HorizontalDemo()
            }
        }
    }
}

@Composable
private fun <K> SelectionBar(state: SwipeSelectState<K>, hint: String, extra: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().background(Color(0xFFF2F3F5)).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Selected: ${state.count}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            extra()
            if (state.isSelectionMode) TextButton(onClick = { state.clear() }) { Text("Clear") }
        }
        Text(hint, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}

// ---- Mail: long press enters selection mode, then the checkbox column ----

private data class Mail(val id: Long, val from: String, val subject: String, val lines: Int)

private val names = listOf("Anna", "Boris", "Vika", "Gleb", "Dasha", "Egor", "Jane", "Ilya")
private val subjects = listOf("Weekly report", "Call at 3 pm", "Invoice #482", "Vacation plans", "PR review", "New design")
private const val PREVIEW = "Message preview that takes one or several lines depending on the email. "

@Composable
private fun MailDemo() {
    val mails = remember {
        List(200) { i -> Mail(i.toLong(), names[i % names.size], "${subjects[i % subjects.size]} #${i + 1}", listOf(1, 2, 3, 4)[(i * 7) % 4]) }
    }
    var side by remember { mutableStateOf(EdgeSide.Start) }
    val state = rememberSwipeSelectState<Long>()
    val listState = rememberLazyListState()
    Column {
        SelectionBar(state, "Hold any email → checkboxes appear → drag along the checkbox column.") {
            SingleChoiceSegmentedButtonRow(Modifier.width(120.dp)) {
                EdgeSide.entries.forEachIndexed { i, s ->
                    SegmentedButton(selected = side == s, onClick = { side = s }, shape = SegmentedButtonDefaults.itemShape(i, 2)) {
                        Text(if (s == EdgeSide.Start) "L" else "R")
                    }
                }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().swipeSelect(
                state, listState,
                keyForIndex = { mails.getOrNull(it)?.id },
                activation = listOf(SwipeSelectActivation.LongPress(`when` = ActivationWhen.Idle), SwipeSelectActivation.Edge(side = side)),
            ),
        ) {
            itemsIndexed(mails, key = { _, m -> m.id }) { _, mail ->
                val selected = state.isSelected(mail.id)
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (selected) Color(0xFFE3EDFF) else Color.White)
                        .clickable { if (state.isSelectionMode) state.toggle(mail.id) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    val box: @Composable () -> Unit = {
                        if (state.isSelectionMode) Checkbox(checked = selected, onCheckedChange = { state.toggle(mail.id) })
                        else Spacer(Modifier.width(16.dp))
                    }
                    if (side == EdgeSide.Start) box()
                    Column(Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(mail.from, fontWeight = FontWeight.Bold)
                        Text(mail.subject)
                        Text(PREVIEW.repeat(mail.lines), color = Color.Gray, maxLines = mail.lines, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (side == EdgeSide.End) box()
                }
                HorizontalDivider()
            }
        }
    }
}

// ---- Photos: long press, then swipe across ----

@Composable
private fun GridDemo() {
    var columns by remember { mutableIntStateOf(4) }
    val state = rememberSwipeSelectState<Int>()
    val gridState = rememberLazyGridState()
    val count = 10_000
    Column {
        SelectionBar(state, "Hold a photo → selection mode. Then swipe sideways to select, vertically to scroll.")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(8.dp)) {
            listOf(2, 3, 4, 6).forEachIndexed { i, n ->
                SegmentedButton(selected = columns == n, onClick = { columns = n }, shape = SegmentedButtonDefaults.itemShape(i, 4)) { Text("$n") }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize().swipeSelect(
                state, gridState,
                keyForIndex = { if (it in 0 until count) it else null },
                activation = listOf(SwipeSelectActivation.LongPress(`when` = ActivationWhen.Idle), SwipeSelectActivation.Pan()),
            ),
        ) {
            items(count, key = { it }) { i ->
                val selected = state.isSelected(i)
                Box(Modifier.aspectRatio(1f).background(Color.hsv(((i * 37) % 360).toFloat(), 0.45f, 0.9f))) {
                    if (selected) {
                        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.3f)))
                        Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF1E66F5),
                            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.White, CircleShape))
                    }
                }
            }
        }
    }
}

// ---- Chat: reverseLayout, newest at the bottom ----

@Composable
private fun ChatDemo() {
    val messages = remember { List(150) { i -> Triple(i, "Message ${i + 1}. ".repeat(1 + (i * 5) % 4), i % 3 == 0) } }
    val state = rememberSwipeSelectState<Int>()
    val listState = rememberLazyListState()
    Column {
        SelectionBar(state, "reverseLayout: #1 is at the bottom. Hold a message and drag up.")
        LazyColumn(
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.fillMaxSize().swipeSelect(
                state, listState,
                keyForIndex = { messages.getOrNull(it)?.first },
                activation = listOf(SwipeSelectActivation.LongPress()),
            ),
        ) {
            items(messages.size, key = { it }) { i ->
                val (id, text, mine) = messages[i]
                val selected = state.isSelected(id)
                Row(
                    Modifier.fillMaxWidth().background(if (selected) Color(0xFFE3EDFF) else Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = 3.dp),
                    horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
                ) {
                    Text(
                        text,
                        color = if (mine) Color.White else Color.Black,
                        modifier = Modifier.widthIn(max = 280.dp)
                            .background(if (mine) Color(0xFF2F80ED) else Color(0xFFE9E9EB), RoundedCornerShape(14.dp))
                            .padding(10.dp),
                    )
                }
            }
        }
    }
}

// ---- Horizontal ----

@Composable
private fun HorizontalDemo() {
    val state = rememberSwipeSelectState<Int>()
    val listState = rememberLazyListState()
    Column {
        SelectionBar(state, "Horizontal list: long press, then drag left / right.")
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.fillMaxWidth().height(200.dp).swipeSelect(
                state, listState,
                keyForIndex = { if (it in 0 until 100) it else null },
                activation = listOf(SwipeSelectActivation.LongPress()),
            ),
        ) {
            items(100, key = { it }) { i ->
                val selected = state.isSelected(i)
                Box(
                    Modifier.width((90 + (i * 13) % 50).dp).height(160.dp)
                        .background(Color.hsv(((i * 29) % 360).toFloat(), 0.5f, 0.85f), RoundedCornerShape(14.dp))
                        .border(4.dp, if (selected) Color(0xFF1E66F5) else Color.Transparent, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${i + 1}", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                    if (selected) Icon(Icons.Filled.CheckCircle, null, tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp))
                }
            }
        }
    }
}

