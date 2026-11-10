package net.neomenia.glideprototype

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun openSettingsUrl(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
}

@Composable
internal fun SettingsDialogs(dialog: String?, state: SettingsState, onDialogChange: (String?) -> Unit) {
    val context = LocalContext.current
    val dismiss = { onDialogChange(null) }
    when {
        dialog == "font" -> AlertDialog(
            onDismissRequest = dismiss, title = { Text("IMEのフォント") },
            text = {
                Column {
                    ImeFonts.NAMES.forEachIndexed { index, name ->
                        SettingsRadio(name, index == state.int("imeFont", 0).coerceIn(ImeFonts.NAMES.indices)) {
                            state.setInt("imeFont", index)
                            dismiss()
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = dismiss) { Text("取消") } },
        )
        dialog == "author" -> AlertDialog(
            onDismissRequest = dismiss,
            text = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(R.drawable.author_avatar), "akky_1023のプロフィール画像",
                        Modifier.size(120.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    TextButton(onClick = { openSettingsUrl(context, "https://neomenia.net") }) { Text("akky_1023") }
                    IconButton(onClick = { openSettingsUrl(context, "https://github.com/akky1023") }) {
                        Icon(painterResource(R.drawable.ic_github), "GitHub")
                    }
                }
            }, confirmButton = { TextButton(onClick = dismiss) { Text("閉じる") } },
        )
        dialog == "licenses" -> AlertDialog(
            onDismissRequest = dismiss, title = { Text("ライセンス") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    LicenseNotices.GROUPS.forEach { group ->
                        TextButton(onClick = { onDialogChange("license:$group") }, modifier = Modifier.fillMaxWidth()) {
                            Text(group, Modifier.fillMaxWidth())
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = dismiss) { Text("閉じる") } },
        )
        dialog?.startsWith("license:") == true -> {
            val group = dialog.removePrefix("license:")
            AlertDialog(onDismissRequest = dismiss, title = { Text(group) }, text = {
                key(group) {
                    val text by produceState("読み込み中…", group) {
                        value = withContext(Dispatchers.IO) {
                            try { LicenseNotices.read(context, group) }
                            catch (error: java.io.IOException) { "ライセンスを読み込めませんでした：${error.localizedMessage}" }
                        }
                    }
                    SelectionContainer {
                        Text(text, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.verticalScroll(rememberScrollState()))
                    }
                }
            }, dismissButton = {
                TextButton(onClick = { onDialogChange("licenses") }) { Text("一覧に戻る") }
            }, confirmButton = { TextButton(onClick = dismiss) { Text("閉じる") } })
        }
    }
}
