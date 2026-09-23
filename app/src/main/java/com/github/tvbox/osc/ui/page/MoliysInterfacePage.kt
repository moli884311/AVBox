package com.github.tvbox.osc.ui.page

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.ui.components.AppTopBarScaffold
import com.github.tvbox.osc.ui.components.LoadState
import com.github.tvbox.osc.ui.components.LoadStateBox
import com.github.tvbox.osc.ui.components.TopBarActionBox
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.HistoryHelper
import com.github.tvbox.osc.util.KV
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** 沫离接口清单地址（服务器静态 JSON，见运维文档） */
private const val MOLIYS_INTERFACE_URL = "https://tvbox.moliys.icu/api/interfaces.json"

private const val SUBSCRIBE_SPLIT = "\t"

private data class MoliysInterfaceItem(
    val name: String? = null,
    val url: String? = null,
    val source: String? = null,
    val size: Long? = null,
)

private data class MoliysInterfaceResponse(
    val items: List<MoliysInterfaceItem>? = null,
)

private fun fetchMoliysInterfaces(): List<MoliysInterfaceItem> {
    val conn = (URL(MOLIYS_INTERFACE_URL).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 20_000
        requestMethod = "GET"
        setRequestProperty("User-Agent", "MoliysShell")
    }
    try {
        val code = conn.responseCode
        if (code !in 200..299) error("HTTP $code")
        val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val response = Gson().fromJson(text, MoliysInterfaceResponse::class.java)
        return response.items.orEmpty()
            .mapNotNull { item ->
                val url = item.url?.trim().orEmpty()
                if (!url.startsWith("http")) null else item.copy(url = url)
            }
    } finally {
        conn.disconnect()
    }
}

private fun rememberMoliysSource(item: MoliysInterfaceItem) {
    val url = item.url ?: return
    val name = item.name?.trim().orEmpty().ifEmpty { url }
    val list = ArrayList(KV.get(HawkConfig.SUBSCRIBE_LIST, ArrayList<String>()))
    if (list.none { it.substringAfter(SUBSCRIBE_SPLIT, it) == url }) {
        list.add(name + SUBSCRIBE_SPLIT + url)
        KV.put(HawkConfig.SUBSCRIBE_LIST, list)
    }
}

private fun applyMoliysSource(item: MoliysInterfaceItem) {
    val url = item.url ?: return
    val followLive = ApiConfig.isLiveFollowVod()
    HistoryHelper.setApiHistory(url)
    KV.put(HawkConfig.API_URL, url)
    if (followLive) KV.put(HawkConfig.LIVE_API_URL, "")
    if (!HistoryHelper.isApiLineHistory(url)) HistoryHelper.clearApiLineList()
    AppBootstrap.onApiUrlChanged()
}

private fun subtitleOf(item: MoliysInterfaceItem): String {
    val source = item.source?.trim().orEmpty()
    val size = item.size ?: 0L
    val sizeText = if (size > 0) {
        val mb = size / 1024.0 / 1024.0
        if (mb >= 1) String.format("%.1f MB", mb) else String.format("%.0f KB", size / 1024.0)
    } else {
        ""
    }
    return listOf(source, sizeText).filter { it.isNotEmpty() }.joinToString(" · ")
}

@Composable
fun MoliysInterfaceScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<MoliysInterfaceItem>>(emptyList()) }
    var activeUrl by remember { mutableStateOf(KV.get(HawkConfig.API_URL, "")) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        loading = true
        error = null
        val result = withContext(Dispatchers.IO) {
            runCatching { fetchMoliysInterfaces() }
        }
        result.onSuccess { list ->
            items = list
            loading = false
        }.onFailure { throwable ->
            error = throwable.message ?: throwable.javaClass.simpleName
            loading = false
        }
    }

    val boxState: LoadState? = when {
        loading -> LoadState.Loading
        error != null -> LoadState.Error(error)
        items.isEmpty() -> LoadState.Empty
        else -> null
    }

    AppTopBarScaffold(
        collapseEnabled = false,
        titleContent = {
            Text(
                text = stringResource(R.string.moliys_iface_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        navigationIcon = {
            TopBarActionBox(
                R.drawable.ic_arrow_left,
                stringResource(R.string.common_back),
                onClick = onNavigateBack,
            )
        },
    ) { topPad, _ ->
        if (boxState != null) {
            LoadStateBox(
                state = boxState,
                emptyText = stringResource(R.string.moliys_iface_empty),
                errorText = stringResource(R.string.config_load_failed),
                retryText = stringResource(R.string.common_retry),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPad),
                onRetry = { reloadKey++ },
            )
            return@AppTopBarScaffold
        }
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = topPad + 12.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(items, key = { index, _ -> index }) { _, item ->
                val url = item.url.orEmpty()
                val name = item.name?.trim().orEmpty().ifEmpty { url }
                val inUse = url == activeUrl
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !inUse) {
                            rememberMoliysSource(item)
                            applyMoliysSource(item)
                            activeUrl = url
                            Toast.makeText(
                                context,
                                context.getString(R.string.config_switched_to, name),
                                Toast.LENGTH_SHORT,
                            ).show()
                            onNavigateBack()
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val subtitle = subtitleOf(item)
                            if (subtitle.isNotEmpty()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (inUse) {
                            Text(
                                text = stringResource(R.string.moliys_iface_in_use),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}
