package com.github.tvbox.osc.ui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.tvbox.osc.R
import com.github.tvbox.osc.ui.components.AVBoxAlertDialog
import com.github.tvbox.osc.ui.components.AppTopBarScaffold
import com.github.tvbox.osc.ui.components.LocalSheetDismissThen
import com.github.tvbox.osc.ui.components.TopBarActionBox
import com.github.tvbox.osc.ui.tv.tvClickable
import com.github.tvbox.osc.util.DanmuSourceStore
import com.github.tvbox.osc.util.DanmakuSpeedTester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 弹幕 API 源管理:内置源 + 自建源,支持单击启停、单条测速、测速排序、恢复默认。
 * 列表顺序即自动匹配的优先级。
 */
@Composable
fun DanmuApiSettingsScreen(onNavigateBack: () -> Unit) {
    val listState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<DanmuSourceStore.Item>>(DanmuSourceStore.load()) }
    var addDialog by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val persist: (List<DanmuSourceStore.Item>) -> Unit = { list ->
        items = list
        DanmuSourceStore.save(list)
    }

    fun toggle(index: Int) {
        persist(items.mapIndexed { i, item ->
            if (i == index) item.copy().also { it.enabled = !it.enabled } else item
        })
    }

    fun testOne(index: Int) {
        val target = items[index].url
        scope.launch {
            val ms = withContext(Dispatchers.IO) { DanmakuSpeedTester.test(target) }
            persist(items.mapIndexed { i, item ->
                if (i == index) item.copy().also { it.latency = ms } else item
            })
        }
    }

    fun testAll(sortAfter: Boolean) {
        if (busy) return
        busy = true
        val snapshot = items
        scope.launch {
            val results = withContext(Dispatchers.IO) {
                snapshot.map { async { DanmakuSpeedTester.test(it.url) } }.awaitAll()
            }
            val updated = snapshot.mapIndexed { i, item -> item.copy().also { it.latency = results[i] } }
            val ordered = if (sortAfter) {
                updated.sortedBy { if (it.latency < 0L) Long.MAX_VALUE else it.latency }
            } else {
                updated
            }
            persist(ordered)
            busy = false
        }
    }

    AppTopBarScaffold(
        titleContent = {
            Text(
                text = stringResource(R.string.settings_danmu_api),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        navigationIcon = {
            TopBarActionBox(R.drawable.ic_arrow_left, stringResource(R.string.common_back), onClick = onNavigateBack)
        },
        actions = {
            TopBarActionBox(R.drawable.ic_subscribe_add, stringResource(R.string.danmu_api_add), onClick = { addDialog = true })
        },
    ) { topPad, navBottom ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(listState)
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp + navBottom),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.height(topPad + 8.dp))

            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(
                    text = stringResource(R.string.danmu_api_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.danmu_api_hint2),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            items.forEachIndexed { index, item ->
                SourceRow(
                    item = item,
                    onClick = { toggle(index) },
                    onTest = { testOne(index) },
                    onDelete = { persist(items.filterIndexed { i, _ -> i != index }) },
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BottomAction(stringResource(R.string.danmu_api_speed_sort), enabled = !busy) { testAll(true) }
                BottomAction(stringResource(R.string.danmu_api_restore_default), enabled = !busy) {
                    busy = true
                    scope.launch {
                        val remote = withContext(Dispatchers.IO) {
                            runCatching { DanmuSourceStore.fetchRemoteDefaults() }.getOrNull()
                        }
                        persist(remote ?: DanmuSourceStore.defaults())
                        busy = false
                    }
                }
                BottomAction(stringResource(R.string.danmu_api_close), enabled = true) { onNavigateBack() }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (addDialog) {
        AddSourceDialog(
            onDismiss = { addDialog = false },
            onConfirm = { name, url ->
                val clean = DanmuSourceStore.cleanUrl(url)
                if (clean == null) {
                    addDialog = false
                } else {
                    val label = name.ifEmpty { DanmuSourceStore.aliasOf(url) }.ifEmpty { clean }
                    persist(items + DanmuSourceStore.Item(label, clean, true))
                    addDialog = false
                }
            },
        )
    }
}

@Composable
private fun SourceRow(
    item: DanmuSourceStore.Item,
    onClick: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val container = if (item.enabled) scheme.primaryContainer else scheme.surfaceContainer
    val onContainer = if (item.enabled) scheme.onPrimaryContainer else scheme.onSurfaceVariant
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .tvClickable(interaction, cornerRadius = 16.dp) { onClick() }
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (item.enabled) scheme.primary else scheme.outline),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = onContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.url,
                style = MaterialTheme.typography.bodySmall,
                color = onContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onTest) {
            Text(
                text = when {
                    item.latency > 0L -> stringResource(R.string.danmu_api_ms, item.latency)
                    item.latency < 0L -> stringResource(R.string.danmu_api_timeout)
                    else -> stringResource(R.string.danmu_api_speed_test)
                },
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(R.string.common_delete),
                tint = onContainer,
            )
        }
    }
}

@Composable
private fun BottomAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) { Text(text) }
}

@Composable
private fun AddSourceDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val dismissThen = LocalSheetDismissThen.current
    AVBoxAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.danmu_api_add)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.danmu_api_name)) },
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.danmu_api_url)) },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = url.isNotBlank(),
                onClick = { dismissThen { onConfirm(name.trim(), url.trim()) } },
            ) {
                Text(stringResource(R.string.common_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
