package net.neomenia.glideprototype

import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.core.content.edit
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(state: SettingsState, initialPage: String?, inputStatus: String) {
    var page by rememberSaveable { mutableStateOf(normalizePage(initialPage)) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val back = { page = if (page.startsWith("board")) "入力" else "" }
    BackHandler(page.isNotEmpty(), back)
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(when (page) {
                "" -> "キーボード設定"
                "board0" -> "記号入力ボード1"
                "board1" -> "記号入力ボード2"
                else -> page
            }) }, navigationIcon = {
                if (page.isNotEmpty()) IconButton(onClick = back) {
                    Icon(painterResource(R.drawable.ic_back), "戻る")
                }
            })
        },
        modifier = Modifier.imePadding(),
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
            // Each page has independent scroll and draft state. Leaving a board discards its draft.
            key(page) {
                Column(
                    Modifier.widthIn(max = 720.dp).fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (page) {
                        "" -> {
                            Heading(stringResource(R.string.app_name))
                            Description(inputStatus)
                            SettingsLink("キーボードを有効にする") {
                                context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                            }
                            SettingsLink("キーボードを切り替える") {
                                context.getSystemService(InputMethodManager::class.java).showInputMethodPicker()
                            }
                            Heading("設定")
                            listOf("入力", "変換", "表示", "このアプリについて").forEach { name ->
                                SettingsLink(name) { page = name }
                            }
                        }
                        "入力" -> InputSettings(state) { page = it }
                        "変換" -> ConversionSettings(state)
                        "表示" -> DisplaySettings(state) { dialog = it }
                        "このアプリについて" -> {
                            Heading(stringResource(R.string.app_name))
                            val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
                            Description("バージョン $version")
                            Description("12キーをなぞって入力する日本語キーボードです。辞書・AI変換は端末内で動作します。")
                            SettingsLink("リポジトリ") { openSettingsUrl(context, "https://github.com/akky1023/Slyde") }
                            SettingsLink("ライセンス") { dialog = "licenses" }
                            SettingsLink("制作者情報") { dialog = "author" }
                        }
                        "board0", "board1" -> SymbolBoardSettings(state, page.last().digitToInt()) { page = "入力" }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
    SettingsDialogs(dialog, state, onDialogChange = { dialog = it })
}

internal fun normalizePage(page: String?): String = page?.takeIf {
    it in setOf("入力", "変換", "表示", "このアプリについて", "board0", "board1")
} ?: ""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputSettings(state: SettingsState, navigate: (String) -> Unit) {
    Heading("編集中文字列")
    PreferenceSwitch(state, "日本語入力モードでは全角英字を使う", "fullWidthComposingAlphabet")
    Heading("入力モード")
    PreferenceSwitch(state, "日本語・アルファベットの入力モードを記憶する", "rememberInputMode")
    PreferenceSwitch(state, "3分経過で日本語入力に戻す", "resetInputModeAfterThreeMinutes",
        enabled = state.boolean("rememberInputMode"))
    Heading("グライド入力")
    SettingsSlider("NAの判定を出しやすくする", 0, 100, 100 - state.int("naNyaBias", 50), step = 5) {
        state.setInt("naNyaBias", 100 - it)
    }
    Description("グライドの軌道がNYA側に曲がってもある程度NA判定が出るようにします．")
    PreferenceSwitch(state, "XEUをグライド入力", "enableEu")
    PreferenceSwitch(state, "XIUをグライド入力", "enableIu")
    Description("子音から始まる -EU/-IU の形の連母音をグライド入力するかを設定します．基本的に日本語では低頻度で -U の誤入力が増えやすくなるためオフを推奨しています．")
    Heading("句読点・括弧")
    val styles = listOf("、。", "，．", "，。", "、．")
    val comma = state.boolean("commaStyle")
    val period = state.boolean("periodStyle")
    val current = if (comma) { if (period) 1 else 2 } else { if (period) 3 else 0 }
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, onExpandedChange = { expanded = it }, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = styles[current], onValueChange = {}, readOnly = true, singleLine = true,
            label = { Text("句読点の種類") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            styles.forEachIndexed { index, label ->
                DropdownMenuItem(text = { Text(label) }, onClick = {
                    state.preferences.edit {
                        putBoolean("commaStyle", index == 1 || index == 2)
                        putBoolean("periodStyle", index == 1 || index == 3)
                    }
                    expanded = false
                })
            }
        }
    }
    SettingsSwitch("丸括弧を半角にする", state.halfWidthParentheses()) { state.setBoolean("halfWidthParentheses", it) }
    Heading("記号入力ボード")
    SettingsLink("記号入力ボード1") { navigate("board0") }
    SettingsLink("記号入力ボード2") { navigate("board1") }
}

@Composable
private fun ConversionSettings(state: SettingsState) {
    val context = LocalContext.current
    SettingsLink("カスタム辞書") { context.startActivity(Intent(context, UserDictionaryActivity::class.java)) }
    Heading("変換")
    PreferenceSwitch(state, "空白キーで変換", "spaceCyclesCandidates")
    PreferenceSwitch(state, "入力再開時に変換を取り消す", "cancelConversionOnInput",
        enabled = state.boolean("spaceCyclesCandidates"))
    Heading("無変換")
    PreferenceSwitch(state, "無変換を有効にする", "alphabetButtonEnabled", fallback = true)
    PreferenceSwitch(state, "入力再開時に無変換を取り消す", "cancelAlphabetOnInput",
        enabled = state.boolean("alphabetButtonEnabled", true))
    Heading("変換候補")
    SettingsSlider("候補欄の行数", 1, 3, state.int("candidateRows", 1), "行") { state.setInt("candidateRows", it) }
    PreferenceSwitch(state, "zenzのオフラインAI変換を有効にする", "zenzEnabled", fallback = true)
    PreferenceSwitch(state, "AI変換を待って候補を表示する", "waitForZenz")
    val enabled = state.boolean("waitForZenz")
    val variable = state.boolean("variableZenzWait")
    Column(Modifier.selectableGroup()) {
        SettingsRadio("固定：20ms", !variable, enabled) { state.setBoolean("variableZenzWait", false) }
        SettingsRadio("可変：20＋文字数×2 ms", variable, enabled) { state.setBoolean("variableZenzWait", true) }
    }
    Description("比較的時間のかかるAI予測変換をある程度待つことで変換予測のちらつきを低減します．")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DisplaySettings(state: SettingsState, showDialog: (String) -> Unit) {
    val context = LocalContext.current
    Heading("テーマ")
    val modes = listOf("端末に合わせる", "ライト", "ダーク")
    val mode = state.int(ThemeMode.KEY, ThemeMode.SYSTEM).let { if (it in modes.indices) it else ThemeMode.SYSTEM }
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, onExpandedChange = { expanded = it }, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(modes[mode], onValueChange = {}, readOnly = true, singleLine = true,
            label = { Text("表示モード") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth())
        ExposedDropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            modes.forEachIndexed { index, name ->
                DropdownMenuItem(text = { Text(name) }, onClick = {
                    expanded = false
                    state.setInt(ThemeMode.KEY, index)
                    (context as? AppCompatActivity)?.let { ThemeMode.apply(it) }
                })
            }
        }
    }
    Heading("キーボード")
    PreferenceSwitch(state, "Composeのデザインを試す", "composeKeyboard")
    Description("キーの表示を切り替えます。「大きさ・位置」でも見た目を確認できます。")
    SettingsLink("大きさ・位置") { context.startActivity(Intent(context, LayoutSettingsActivity::class.java)) }
    SettingsLink("フローティングの大きさ・位置") {
        context.startActivity(Intent(context, LayoutSettingsActivity::class.java).putExtra(LayoutSettingsActivity.EXTRA_FLOATING, true))
    }
    Heading("フォント")
    Description("現在のフォント：${ImeFonts.NAMES[state.int("imeFont", 0).coerceIn(ImeFonts.NAMES.indices)]}")
    SettingsLink("フォントを選ぶ") { showDialog("font") }
    SettingsSlider("変換候補の文字サイズ", 12, 30, state.int("candidateTextSp", 18), "sp") { state.setInt("candidateTextSp", it) }
    PreferenceSwitch(state, "紛らわしい文字の注記を表示（全・半・漢・カナなど）", "showCharacterBadges", fallback = true)
    PreferenceSwitch(state, "AI候補の「AI」を表示", "showAiBadges", fallback = true)
}

@Composable
internal fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
internal fun Description(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun SettingsLink(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(text, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun PreferenceSwitch(state: SettingsState, title: String, key: String, fallback: Boolean = false, enabled: Boolean = true) {
    SettingsSwitch(title, state.boolean(key, fallback), enabled) { state.setBoolean(key, it) }
}

@Composable
internal fun SettingsSwitch(title: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, enabled, Role.Switch, onChange).heightIn(min = 56.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(title, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .38f))
        Switch(checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
internal fun SettingsRadio(title: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, enabled, Role.RadioButton, onClick).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick = null, enabled = enabled)
        Text(title, modifier = Modifier.padding(start = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .38f))
    }
}

@Composable
private fun SettingsSlider(title: String, min: Int, max: Int, value: Int, suffix: String = "", step: Int = 1, onChange: (Int) -> Unit) {
    Column(Modifier.padding(top = 12.dp)) {
        Text("$title（$value$suffix）", style = MaterialTheme.typography.bodyLarge)
        Slider(value.coerceIn(min, max).toFloat(), onValueChange = { onChange(it.roundToInt()) },
            valueRange = min.toFloat()..max.toFloat(), steps = (max - min) / step - 1,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = title },
        )
    }
}
