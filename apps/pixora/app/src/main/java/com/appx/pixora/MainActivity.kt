package com.appx.pixora

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.Html
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import java.util.Locale

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

/** Search Wikimedia Commons. It is a public image library and does not require an API key. */
private suspend fun searchCommons(term: String): List<Photo> = withContext(Dispatchers.IO) {
    val encoded = URLEncoder.encode("filetype:bitmap $term", "UTF-8")
    val endpoint = "https://commons.wikimedia.org/w/api.php?action=query&format=json" +
        "&generator=search&gsrsearch=$encoded&gsrnamespace=6&gsrlimit=30" +
        "&prop=imageinfo&iiprop=url%7Cextmetadata&iiurlwidth=640"
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 15000
        readTimeout = 20000
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "PixoraAndroid/1.0 (Android image gallery)")
    }
    try {
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("سرویس Wikimedia پاسخ نداد (${connection.responseCode})")
        }
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages")
            ?: return@withContext emptyList()
        buildList {
            val keys = pages.keys()
            while (keys.hasNext()) {
                val page = pages.optJSONObject(keys.next()) ?: continue
                val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                val original = info.optString("url")
                val thumb = info.optString("thumburl").ifBlank { original }
                if (!original.startsWith("https://") || !thumb.startsWith("https://")) continue
                val metadata = info.optJSONObject("extmetadata")
                fun metadataText(key: String): String {
                    val raw = metadata?.optJSONObject(key)?.optString("value").orEmpty()
                    return Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString()
                        .replace(Regex("\\s+"), " ").trim()
                }
                add(
                    Photo(
                        id = page.optString("pageid"),
                        title = page.optString("title").substringAfter("File:")
                            .ifBlank { "بدون عنوان" },
                        thumbnail = thumb,
                        imageUrl = original,
                        pageUrl = info.optString("descriptionurl"),
                        creator = metadataText("Artist").ifBlank { "نامشخص" },
                        license = metadataText("LicenseShortName").ifBlank { "مجوز را در صفحهٔ منبع بررسی کن" }
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
    var selectedPhoto by remember { mutableStateOf<Photo?>(null) }

    BackHandler(enabled = selectedPhoto != null) { selectedPhoto = null }

    LaunchedEffect(submittedQuery) {
        loading = true
        error = null
        try {
            photos = searchCommons(submittedQuery)
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
                    navigationIcon = {
                        if (selectedPhoto != null) {
                            IconButton(onClick = { selectedPhoto = null }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت")
                            }
                        }
                    },
                    title = {
                        Column {
                            Text(if (selectedPhoto == null) "Pixora" else "جزئیات عکس",
                                fontWeight = FontWeight.ExtraBold)
                            Text(
                                if (selectedPhoto == null) "جست‌وجو در Wikimedia Commons" else "اطلاعات و دریافت تصویر",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    actions = {
                        if (selectedPhoto == null) {
                            IconButton(onClick = { submittedQuery = query.ifBlank { "nature" } }) {
                                Icon(Icons.Default.Refresh, contentDescription = "تازه‌سازی")
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (selectedPhoto == null) {
                    NavigationBar {
                        NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                            icon = { Icon(Icons.Default.Explore, null) }, label = { Text("اکتشاف") })
                        NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                            icon = { Icon(Icons.Default.Favorite, null) }, label = { Text("پسندیده‌ها") })
                    }
                }
            }
        ) { padding ->
            if (selectedPhoto != null) {
                PhotoDetails(
                    photo = selectedPhoto!!,
                    liked = selectedPhoto!!.id in favorites,
                    onLike = {
                        val id = selectedPhoto!!.id
                        if (id in favorites) favorites.remove(id) else favorites.add(id)
                        prefs.edit().putStringSet("favorites", favorites.toSet()).apply()
                    },
                    onDownload = { downloadPhoto(context, selectedPhoto!!) },
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
            } else {
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
                                Text("اتصال به Wikimedia Commons ممکن است موقتاً برقرار نباشد.")
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
                                    onOpen = { selectedPhoto = photo }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoDetails(
    photo: Photo,
    liked: Boolean,
    onLike: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        AsyncImage(
            model = photo.imageUrl,
            contentDescription = photo.title,
            modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp, max = 460.dp)
                .clip(RoundedCornerShape(20.dp)),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.height(14.dp))
        Text(photo.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("عکاس: ${photo.creator}", style = MaterialTheme.typography.bodyMedium)
        Text("مجوز: ${photo.license}", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onDownload, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("دانلود عکس")
            }
            OutlinedButton(onClick = onLike, modifier = Modifier.weight(1f)) {
                Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (liked) "پسندیده شد" else "پسندیدن")
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(photo.pageUrl))
                    // The source page is opened only after the user taps this button.
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = photo.pageUrl.startsWith("https://")
        ) {
            Icon(Icons.Default.OpenInNew, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("مشاهده صفحهٔ اصلی و شرایط مجوز")
        }
        Text(
            "تصاویر از Wikimedia Commons هستند. پیش از استفادهٔ دوباره، شرایط مجوز هر تصویر را در صفحهٔ منبع بخوان.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)
        )
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
                modifier = Modifier.fillMaxWidth().height(190.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .clickable(onClick = onOpen),
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
                        Icon(Icons.Default.Info, contentDescription = "جزئیات عکس")
                    }
                }
            }
        }
    }
}

private fun downloadPhoto(context: Context, photo: Photo) {
    try {
        val imageUri = Uri.parse(photo.imageUrl)
        val extension = imageUri.lastPathSegment?.substringAfterLast('.', "jpg")
            ?.lowercase(Locale.ROOT)?.takeIf { it in setOf("jpg", "jpeg", "png", "webp", "gif", "tif", "tiff") }
            ?: "jpg"
        val safeName = photo.title
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
            .trim('_').take(48).ifBlank { "Pixora_${photo.id}" }
        val mime = when (extension) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "tif", "tiff" -> "image/tiff"
            else -> "image/jpeg"
        }
        val request = DownloadManager.Request(imageUri)
            .setTitle("Pixora - ${photo.title}")
            .setDescription("ذخیره از Wikimedia Commons در پوشهٔ Downloads")
            .setMimeType(mime)
            .addRequestHeader("User-Agent", "PixoraAndroid/1.0")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "Pixora_${safeName}.$extension"
            )
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        Toast.makeText(context, "دانلود شروع شد؛ فایل در پوشهٔ Downloads ذخیره می‌شود.", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "شروع دانلود ممکن نشد: ${e.localizedMessage ?: "خطای ناشناخته"}",
            Toast.LENGTH_LONG).show()
    }
}
