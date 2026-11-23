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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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

private data class Photo(val id: Int, val title: String, val url: String)

private val samplePhotos = (1..24).map { id ->
    Photo(id, "Photo $id", "https://picsum.photos/seed/pixora$id/700/900")
}

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PixoraApp(incomingUri: Uri?) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pixora", Context.MODE_PRIVATE) }
    val favorites = remember {
        mutableStateListOf<Int>().apply {
            addAll(prefs.getStringSet("favorites", emptySet())!!.mapNotNull { it.toIntOrNull() })
        }
    }
    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var sharedPhoto by remember(incomingUri) { mutableStateOf(incomingUri) }

    val visiblePhotos = (if (tab == 1) samplePhotos.filter { it.id in favorites } else samplePhotos)
        .filter { it.title.contains(query, ignoreCase = true) }

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
                            Text("دنیای عکس‌ها", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            Toast.makeText(context, "عکس‌ها فعلاً نمونه هستند؛ اتصال سرویس آنلاین در مرحله بعد انجام می‌شود.", Toast.LENGTH_LONG).show()
                        }) { Icon(Icons.Default.Cloud, contentDescription = "Online") }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.Explore, null) }, label = { Text("اکتشاف") })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.Favorite, null) }, label = { Text("پسندیده‌ها") })
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp)) {
                if (sharedPhoto != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEDEAFF)),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("یک عکس از برنامه‌ای دیگر دریافت شد", fontWeight = FontWeight.Bold)
                            Text("می‌توانی آن را برای خودت نگه داری. هنوز جایی منتشر نشده.")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    Toast.makeText(context, "عکس دریافتی آماده مشاهده است.", Toast.LENGTH_SHORT).show()
                                    sharedPhoto = null
                                }) { Text("متوجه شدم") }
                                TextButton(onClick = { sharedPhoto = null }) { Text("بستن") }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp),
                    placeholder = { Text("جست‌وجو در عکس‌ها...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp)
                )
                if (tab == 1 && visiblePhotos.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("هنوز عکسی پسندیده نشده ❤️")
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(bottom = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(visiblePhotos, key = { it.id }) { photo ->
                            PhotoCard(
                                photo = photo,
                                liked = photo.id in favorites,
                                onLike = {
                                    if (photo.id in favorites) favorites.remove(photo.id) else favorites.add(photo.id)
                                    prefs.edit().putStringSet("favorites", favorites.map { it.toString() }.toSet()).apply()
                                },
                                onDownload = { downloadPhoto(context, photo) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoCard(photo: Photo, liked: Boolean, onLike: () -> Unit, onDownload: () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Column {
            AsyncImage(
                model = photo.url,
                contentDescription = photo.title,
                modifier = Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
                contentScale = ContentScale.Crop
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(photo.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = onLike) {
                    Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Like",
                        tint = if (liked) Color(0xFFE64A72) else Color.Gray)
                }
                IconButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, contentDescription = "Download")
                }
            }
        }
    }
}

private fun downloadPhoto(context: Context, photo: Photo) {
    try {
        val request = DownloadManager.Request(Uri.parse(photo.url))
            .setTitle("Pixora - ${photo.title}")
            .setDescription("در حال ذخیره عکس")
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
