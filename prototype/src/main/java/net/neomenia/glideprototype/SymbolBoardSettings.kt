package net.neomenia.glideprototype

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun SymbolBoardSettings(state: SettingsState, board: Int, onSaved: () -> Unit) {
    var values by rememberSaveable(board) { mutableStateOf(SymbolBoards.load(state.preferences, board).toList()) }
    var returnAfter by rememberSaveable(board) { mutableStateOf(SymbolBoards.returns(state.preferences, board)) }
    Description("各キーに入力する記号を設定します。空欄のキーは数字配列への「戻る」になります。")
    repeat(4) { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { col ->
                val index = row * 3 + col
                OutlinedTextField(values[index], onValueChange = { value ->
                    values = values.toMutableList().also { it[index] = value }
                }, label = { Text("${row + 1}行${col + 1}列") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false), modifier = Modifier.weight(1f))
            }
        }
    }
    SettingsSwitch("記号入力後に数字配列へ戻る", returnAfter) { returnAfter = it }
    SettingsLink("初期配置に戻す") {
        values = SymbolBoards.defaults(board).toList()
        returnAfter = true
    }
    Button(onClick = {
        SymbolBoards.save(state.preferences, board, values.toTypedArray(), returnAfter)
        onSaved()
    }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("保存") }
}
