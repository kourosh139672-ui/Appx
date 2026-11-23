package com.appx.pixora

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private data class Photo(
    val id: String,
    val title: String,
    val thumbnail: String,
    val imageUrl: String,
    val pageUrl: String,
    val creator: String,
    val license: String
)

class MainActivity : ComponentActivity() {
    private var incomingUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingUri = extractSharedImage(intent)
        setContent { PixoraApp(incomingUri) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingUri = extractSharedImage(intent)
    }

    private fun extractSharedImage(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_SEND) return null
        return if (intent.type?.startsWith("image/") == true) {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        } else null
    }
}

private suspend fun searchOpenverse(term: String, page: Int = 1): List<Photo> =
    withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(term, "UTF-8")
        val url = URL("https://api.openverse.org/v1/images/?q=$encoded&page=$page&page_size=20")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 18000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "PixoraAndroid/1.0")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("سرویس عکس پاسخ نداد (${connection.responseCode})")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val results = JSONObject(body).optJSONArray("results") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until results.length()) {
                    val item = results.optJSONObject(i) ?: continue
                    val thumb = item.optString("thumbnail")
                    val image = item.optString("url")
                    if (thumb.isBlank() || image.isBlank() || thumb == "null" || image == "null") continue
                    add(
                        Photo(
                            id = item.optString("id", "$i"),
                            title = item.optString("title").ifBlank { "بدون عنوان" },
                            thumbnail = thumb,
                            imageUrl = image,
                            pageUrl = item.optString("foreign_landing_url"),
                            creator = item.optString("creator").ifBlank { "نامشخص" },
                            license = item.optString("license").ifBlank { "مجوز نامشخص" }
                        )
                    )
                }
            }
        } finally {
            connection.disconnect()
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PixoraApp(incomingUri: Uri?) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pixora", Context.MODE_PRIVATE) }
    val favorites = remember {
        mutableStateListOf<String>().apply {
            addAll(prefs.getStringSet("favorites", emptySet()).orEmpty())
        }
    }
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("nature") }
    var submittedQuery by remember { mutableStateOf("nature") }
    var photos by remember { mutableStateOf<List<Photo>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sharedPhoto by remember(incomingUri) { mutableStateOf(incomingUri) }

    LaunchedEffect(submittedQuery) {
        loading = true
        error = null
        try {
            photos = searchOpenverse(submittedQuery)
        } catch (e: Exception) {
            error = e.message ?: "ارتباط با سرویس عکس برقرار نشد."
            photos = emptyList()
        } finally {
            loading = false
        }
    }

    val visiblePhotos = if (tab == 1) photos.filter { it.id in favorites } else photos

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF6654D9),
            secondary = Color(0xFF00A99D),
            background = Color(0xFFF7F6FC)
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Pixora", fontWeight = FontWeight.ExtraBold)
                            Text("جست‌وجوی عکس در Openverse", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { submittedQuery = query.ifBlank { "nature" } }) {
                            Icon(Icons.Default.Refresh, contentDescription = "تازه‌سازی")
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                        icon = { Icon(Icons.Default.Explore, null) }, label = { Text("اکتشاف") })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                        icon = { Icon(Icons.Default.Favorite, null) }, label = { Text("پسندیده‌ها") })
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp)) {
                if (sharedPhoto != null) {
                    Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("یک عکس از برنامه‌ای دیگر دریافت شد", fontWeight = FontWeight.Bold)
                            Text("این عکس فقط از طریق اشتراک‌گذاری خودت به Pixora رسیده است.")
                            TextButton(onClick = { sharedPhoto = null }) { Text("بستن") }
                        }
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                    placeholder = { Text("مثلاً cat, nature, ocean...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        IconButton(onClick = { submittedQuery = query.trim().ifBlank { "nature" }; tab = 0 }) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "جست‌وجو")
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp)
                )
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("طبیعت" to "nature", "گربه" to "cat", "دریا" to "ocean",
                        "فضا" to "space", "ماشین" to "car", "گل" to "flower").forEach { (label, value) ->
                        FilterChip(
                            selected = submittedQuery == value && tab == 0,
                            onClick = { query = value; submittedQuery = value; tab = 0 },
                            label = { Text(label) }
                        )
                    }
                }
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(error ?: "خطا")
                            Text("ممکن است سرویس Openverse موقتاً در دسترس نباشد.")
                            Button(onClick = { submittedQuery = query.trim().ifBlank { "nature" } }) {
                                Text("تلاش دوباره")
                            }
                        }
                    }
                    tab == 1 && visiblePhotos.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("هنوز عکسی پسندیده نشده ❤️")
                    }
                    visiblePhotos.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("عکسی پیدا نشد. عبارت دیگری را امتحان کن.")
                    }
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(visiblePhotos, key = { it.id }) { photo ->
                            PhotoCard(
                                photo = photo,
                                liked = photo.id in favorites,
                                onLike = {
                                    if (photo.id in favorites) favorites.remove(photo.id) else favorites.add(photo.id)
                                    prefs.edit().putStringSet("favorites", favorites.toSet()).apply()
                                },
                                onDownload = { downloadPhoto(context, photo) },
                                onOpen = {
                                    if (photo.pageUrl.isNotBlank()) {
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(photo.pageUrl))) }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoCard(
    photo: Photo,
    liked: Boolean,
    onLike: () -> Unit,
    onDownload: () -> Unit,
    onOpen: () -> Unit
) {
    Card(shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Column {
            AsyncImage(
                model = photo.thumbnail,
                contentDescription = photo.title,
                modifier = Modifier.fillMaxWidth().height(190.dp).clip(
                    RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
                contentScale = ContentScale.Crop
            )
            Column(Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                Text(photo.title, style = MaterialTheme.typography.labelMedium, maxLines = 2)
                Text("عکاس: ${photo.creator}", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                Text("مجوز: ${photo.license}", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onLike, modifier = Modifier.weight(1f)) {
                        Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "پسندیدن",
                            tint = if (liked) Color(0xFFE64A72) else Color.Gray)
                    }
                    IconButton(onClick = onDownload, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Download, contentDescription = "دانلود")
                    }
                    IconButton(onClick = onOpen, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Info, contentDescription = "جزئیات و منبع")
                    }
                }
            }
        }
    }
}

private fun downloadPhoto(context: Context, photo: Photo) {
    try {
        val request = DownloadManager.Request(Uri.parse(photo.imageUrl))
            .setTitle("Pixora - ${photo.title}")
            .setDescription("ذخیره عکس از Openverse")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "Pixora/${photo.id}.jpg")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        Toast.makeText(context, "دانلود شروع شد.", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        Toast.makeText(context, "دانلود انجام نشد. اتصال اینترنت را بررسی کن.", Toast.LENGTH_LONG).show()
    }
}
