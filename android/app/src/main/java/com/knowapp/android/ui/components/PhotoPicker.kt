package com.knowapp.android.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import java.io.File

// Take a photo or choose one; shows a preview with a way to remove it
@Composable
fun PhotoPicker(photoUri: Uri?, onPhoto: (Uri?) -> Unit, modifier: Modifier = Modifier, allowCamera: Boolean = true) {
    val context = LocalContext.current
    var captureUri by remember { mutableStateOf<Uri?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) onPhoto(uri) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) onPhoto(captureUri) }

    fun startCamera() {
        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        captureUri = uri
        camera.launch(uri)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (photoUri != null) {
            AsyncImage(model = photoUri, contentDescription = "Selected photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(16.dp)))
            TextButton(onClick = { onPhoto(null) }) { Text("Remove photo") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (allowCamera) {
                    OutlinedButton(onClick = { startCamera() }, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Take photo", modifier = Modifier.padding(start = 6.dp))
                    }
                }
                OutlinedButton(onClick = { gallery.launch("image/*") }, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Choose photo", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}
