package net.neomenia.glideprototype

import android.content.SharedPreferences
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ComposeSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<SettingsActivity>()
    private val preferences: SharedPreferences get() = InputPreferences.get(compose.activity)

    @Before fun clearSettings() { compose.runOnIdle { preferences.edit().clear().commit() } }

    private fun click(text: String) { compose.onNodeWithText(text).performScrollTo().performClick() }
    private fun back() { compose.onNodeWithContentDescription("戻る").performClick() }
    private fun pref(key: String, fallback: Boolean = false) = preferences.getBoolean(key, fallback)
    private fun capture(name: String) {
        compose.runOnIdle {
            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
            val view = if (dialog?.isShowing == true) dialog.window!!.decorView else compose.activity.window.decorView
            SettingsActivityTest.capture(view, name, 360, 800)
        }
    }

    @Test fun homeOffersFourGroupsAndBoardSaveReturnsToInput() {
        listOf("入力", "変換", "表示", "このアプリについて").forEach {
            compose.onNodeWithText(it).assertHasClickAction()
        }
        compose.onNodeWithText("入力を練習する").assertDoesNotExist()
        capture("compose-settings-home")
        click("入力")
        compose.onNodeWithText("総合記号ボード").assertDoesNotExist()
        click("記号入力ボード1")
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(12)
        click("保存")
        compose.onNodeWithText("記号入力ボード2").assertExists()
        back()
        click("表示")
        capture("compose-settings-display")
    }

    @Test fun booleanSettingsPersistAcrossNavigation() {
        click("入力")
        val title = "日本語入力モードでは全角英字を使う"
        compose.onNodeWithText(title).assertIsOff()
        click(title)
        compose.runOnIdle { assertTrue(pref("fullWidthComposingAlphabet")) }
        back(); click("入力")
        compose.onNodeWithText(title).assertIsOn()
        click(title)
        compose.runOnIdle { assertFalse(pref("fullWidthComposingAlphabet")) }
        back(); click("表示")
        click("AI候補の「AI」を表示")
        compose.runOnIdle { assertFalse(pref("showAiBadges", true)) }
        back(); click("表示")
        compose.onNodeWithText("AI候補の「AI」を表示").assertIsOff()
    }

    @Test fun dependentConversionSwitchesKeepTheirSavedValues() {
        click("変換")
        val cycle = "空白キーで変換"
        val cancel = "入力再開時に変換を取り消す"
        compose.onNodeWithText(cycle).assertIsOff()
        compose.onNodeWithText(cancel).assertIsNotEnabled().assertIsOff()
        click(cycle); click(cancel); click(cycle)
        compose.onNodeWithText(cancel).assertIsNotEnabled().assertIsOn()
        compose.runOnIdle {
            assertFalse(pref("spaceCyclesCandidates"))
            assertTrue(pref("cancelConversionOnInput"))
        }
        val alphabet = "無変換を有効にする"
        val cancelAlphabet = "入力再開時に無変換を取り消す"
        compose.onNodeWithText(alphabet).assertIsOn()
        click(cancelAlphabet); click(alphabet)
        compose.onNodeWithText(cancelAlphabet).assertIsNotEnabled().assertIsOn()
        back(); click("変換")
        compose.onNodeWithText(cancelAlphabet).assertIsNotEnabled().assertIsOn()
        click(alphabet)
        compose.onNodeWithText(cancelAlphabet).assertIsEnabled().assertIsOn()
    }

    @Test fun rememberingModeControlsResetAndClearsStaleMode() {
        click("入力")
        val remember = "日本語・アルファベットの入力モードを記憶する"
        val reset = "3分経過で日本語入力に戻す"
        compose.onNodeWithText(reset).assertIsNotEnabled()
        click(remember); click(reset)
        compose.runOnIdle {
            preferences.edit().putBoolean("savedJapaneseInputMode", false).putLong("inputModeHiddenAt", 42).commit()
        }
        click(remember)
        compose.onNodeWithText(reset).assertIsNotEnabled().assertIsOn()
        compose.runOnIdle {
            assertFalse(preferences.contains("savedJapaneseInputMode"))
            assertFalse(preferences.contains("inputModeHiddenAt"))
        }
    }

    @Test fun glideOptionsAndLegacyParenthesesKeepTheirDefaults() {
        compose.runOnIdle { preferences.edit().putBoolean("openParenStyle", true).commit() }
        click("入力")
        compose.onNodeWithText("XEUをグライド入力").assertIsOff()
        compose.onNodeWithText("XIUをグライド入力").assertIsOff()
        compose.onNodeWithText("丸括弧を半角にする").assertIsOn()
        click("丸括弧を半角にする")
        compose.runOnIdle { assertFalse(InputPreferences.halfWidthParentheses(preferences)) }
        back(); click("入力")
        compose.onNodeWithText("丸括弧を半角にする").assertIsOff()
        click("XEUをグライド入力"); click("XIUをグライド入力")
        compose.runOnIdle { assertTrue(pref("enableEu")); assertTrue(pref("enableIu")) }
    }

    @Test fun punctuationMenuSavesAllFourPairs() {
        click("入力")
        val styles = listOf("、。", "，．", "，。", "、．")
        styles.forEachIndexed { index, style ->
            compose.onNodeWithText("句読点の種類").performScrollTo().performClick()
            compose.onNode(hasText(style) and hasAnyAncestor(isPopup())).performClick()
            compose.runOnIdle {
                assertEquals(index == 1 || index == 2, pref("commaStyle"))
                assertEquals(index == 1 || index == 3, pref("periodStyle"))
            }
            compose.onNodeWithText("句読点の種類").assertTextContains(style)
        }
        capture("compose-settings-punctuation")
    }

    @Test fun waitModesEnableAndPersist() {
        click("変換")
        compose.onNodeWithText("固定：20ms").assertIsSelected()
        compose.onNodeWithText("可変：20＋文字数×2 ms").assertIsNotEnabled()
        click("AI変換を待って候補を表示する")
        click("可変：20＋文字数×2 ms")
        compose.runOnIdle { assertTrue(pref("waitForZenz")); assertTrue(pref("variableZenzWait")) }
        back(); click("変換")
        compose.onNodeWithText("可変：20＋文字数×2 ms").assertIsSelected()
        click("固定：20ms")
        compose.runOnIdle { assertFalse(pref("variableZenzWait")) }
    }

    @Test fun slidersPreserveBiasDirectionAndSaveIntegerValues() {
        click("入力")
        compose.onNodeWithContentDescription("NAの判定を出しやすくする").performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(70f) }
        compose.runOnIdle { assertEquals(30, preferences.getInt("naNyaBias", -1)) }
        back(); click("変換")
        compose.onNodeWithContentDescription("候補欄の行数").performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(3f) }
        compose.runOnIdle { assertEquals(3, preferences.getInt("candidateRows", -1)) }
        back(); click("表示")
        compose.onNodeWithContentDescription("変換候補の文字サイズ").performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(24f) }
        compose.runOnIdle { assertEquals(24, preferences.getInt("candidateTextSp", -1)) }
    }

    @Test fun biasSliderUsesFivePointStepsAndPreservesExistingSettingUntilEdited() {
        compose.runOnIdle { preferences.edit().putInt("naNyaBias", 47).commit() }
        click("入力")
        val slider = compose.onNodeWithContentDescription("NAの判定を出しやすくする").performScrollTo()
        val range = slider.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(19, range.steps)
        compose.runOnIdle { assertEquals(47, preferences.getInt("naNyaBias", -1)) }
        slider.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(73f) }
        compose.runOnIdle { assertEquals(25, preferences.getInt("naNyaBias", -1)) }
    }

    @Test fun fontDialogSavesSelection() {
        click("表示"); click("フォントを選ぶ")
        compose.onNodeWithText(ImeFonts.NAMES[1]).performClick()
        compose.onNodeWithText("現在のフォント：${ImeFonts.NAMES[1]}").assertExists()
        compose.runOnIdle { assertEquals(1, preferences.getInt("imeFont", -1)) }
    }

    @Test fun composeKeyboardDesignCanBeEnabledAndDisabled() {
        click("表示")
        compose.onNodeWithText("Composeのデザインを試す").assertIsOff()
        click("Composeのデザインを試す")
        compose.runOnIdle { assertTrue(pref("composeKeyboard")) }
        back(); click("表示")
        compose.onNodeWithText("Composeのデザインを試す").assertIsOn()
        click("Composeのデザインを試す")
        compose.runOnIdle { assertFalse(pref("composeKeyboard")) }
    }

    private fun selectTheme(name: String) {
        compose.onNodeWithText("表示モード").performScrollTo().performClick()
        compose.onNode(hasText(name) and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithText("表示モード").assertTextContains(name)
    }

    @Test fun themeSelectionAppliesImmediatelyAndSurvivesRecreation() {
        click("表示")
        compose.onNodeWithText("表示モード").assertTextContains("端末に合わせる")
        selectTheme("ダーク")
        compose.runOnIdle {
            assertEquals(ThemeMode.DARK, preferences.getInt(ThemeMode.KEY, -1))
            assertEquals(android.content.res.Configuration.UI_MODE_NIGHT_YES,
                compose.activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK)
        }
        capture("compose-settings-theme-dark")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("表示モード").assertTextContains("ダーク")
        selectTheme("ライト")
        compose.runOnIdle {
            assertEquals(android.content.res.Configuration.UI_MODE_NIGHT_NO,
                compose.activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK)
        }
        capture("compose-settings-theme-light")
        selectTheme("端末に合わせる")
        compose.runOnIdle { assertEquals(ThemeMode.SYSTEM, preferences.getInt(ThemeMode.KEY, -1)) }
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun forcedLightCanReturnToTheDevicesDarkTheme() {
        click("表示")
        selectTheme("ライト")
        compose.runOnIdle {
            assertEquals(android.content.res.Configuration.UI_MODE_NIGHT_NO,
                compose.activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK)
        }
        // Robolectric shares Activity overrides with app resources; restore the device configuration.
        org.robolectric.RuntimeEnvironment.setQualifiers("w360dp-h800dp-night")
        selectTheme("端末に合わせる")
        compose.runOnIdle {
            assertEquals(android.content.res.Configuration.UI_MODE_NIGHT_YES,
                compose.activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK)
        }
    }

    @Test fun editorLinksKeepTheirDestinations() {
        click("表示"); click("大きさ・位置")
        compose.runOnIdle {
            val intent = shadowOf(compose.activity).nextStartedActivity
            assertEquals(LayoutSettingsActivity::class.java.name, intent.component!!.className)
            assertFalse(intent.getBooleanExtra(LayoutSettingsActivity.EXTRA_FLOATING, false))
        }
        click("フローティングの大きさ・位置")
        compose.runOnIdle { assertTrue(shadowOf(compose.activity).nextStartedActivity.getBooleanExtra(LayoutSettingsActivity.EXTRA_FLOATING, false)) }
        back(); click("変換"); click("カスタム辞書")
        compose.runOnIdle { assertEquals(UserDictionaryActivity::class.java.name, shadowOf(compose.activity).nextStartedActivity.component!!.className) }
    }

    @Test fun boardDraftSurvivesRecreationAndOnlySavesOnSave() {
        click("入力"); click("記号入力ボード1")
        compose.onNodeWithText("1行1列").performTextReplacement("★")
        compose.runOnIdle { assertEquals("*", SymbolBoards.load(preferences, 0)[0]) }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("★").assertExists()
        click("記号入力後に数字配列へ戻る"); click("保存")
        compose.runOnIdle {
            assertEquals("★", SymbolBoards.load(preferences, 0)[0])
            assertFalse(SymbolBoards.returns(preferences, 0))
            assertEquals("'", SymbolBoards.load(preferences, 1)[0])
        }
        click("記号入力ボード1"); click("初期配置に戻す"); click("保存")
        compose.runOnIdle {
            assertArrayEquals(SymbolBoards.defaults(0), SymbolBoards.load(preferences, 0))
            assertTrue(SymbolBoards.returns(preferences, 0))
        }
    }

    @Test fun boardBackDiscardsUnsavedChanges() {
        click("入力"); click("記号入力ボード2")
        compose.onNodeWithText("1行1列").performTextReplacement("★")
        back(); click("記号入力ボード2")
        compose.onNodeWithText("★").assertDoesNotExist()
        compose.runOnIdle { assertArrayEquals(SymbolBoards.defaults(1), SymbolBoards.load(preferences, 1)) }
    }

    @Test fun aboutLinksAndAuthorLinksKeepTheirDestinations() {
        click("このアプリについて"); click("リポジトリ")
        compose.runOnIdle { assertEquals("https://github.com/akky1023/Slyde", shadowOf(compose.activity).nextStartedActivity.dataString) }
        click("制作者情報")
        compose.onNodeWithContentDescription("akky_1023のプロフィール画像").assertExists()
        compose.onNodeWithText("akky_1023").performClick()
        compose.runOnIdle { assertEquals("https://neomenia.net", shadowOf(compose.activity).nextStartedActivity.dataString) }
        compose.onNodeWithContentDescription("GitHub").performClick()
        compose.runOnIdle { assertEquals("https://github.com/akky1023", shadowOf(compose.activity).nextStartedActivity.dataString) }
        capture("compose-settings-author")
    }

    @Test fun licensesKeepEveryGroupAndTheirNotices() {
        click("このアプリについて"); click("ライセンス")
        LicenseNotices.GROUPS.forEach { group ->
            compose.onNodeWithText(group).performClick()
            try {
                compose.waitUntil(10_000) {
                    shadowOf(android.os.Looper.getMainLooper()).idle()
                    compose.onAllNodes(hasText("■ ", substring = true)).fetchSemanticsNodes().isNotEmpty()
                }
            } catch (error: ComposeTimeoutException) {
                throw AssertionError(compose.onAllNodes(isRoot()).printToString(maxDepth = 10), error)
            }
            compose.onNode(hasText("■ ", substring = true)).assertExists()
            compose.onNodeWithText("一覧に戻る").performClick()
        }
    }

    @Test
    @Config(qualifiers = "w840dp-h600dp-land-night")
    fun wideDarkScreenKeepsContentCenteredAndReadable() {
        click("表示")
        compose.onNodeWithText("現在のフォント：System").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            SettingsActivityTest.capture(view, "compose-settings-wide-dark", 840, 600)
        }
        val bounds = compose.onNodeWithText("フォントを選ぶ").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.left > 40f)
        assertTrue(bounds.right < 800f)
    }

    @Test fun largeTextKeepsSettingsAndBoardSaveReachable() {
        org.robolectric.RuntimeEnvironment.setFontScale(1.5f)
        compose.activityRule.scenario.recreate()
        click("入力"); click("記号入力ボード2")
        click("保存")
        compose.onNodeWithText("記号入力ボード1").assertExists()
        back(); click("表示")
        click("AI候補の「AI」を表示")
        compose.runOnIdle { assertFalse(pref("showAiBadges", true)) }
    }
}
