package net.neomenia.glideprototype

import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeKeyboardTest {
    @get:Rule val compose = createAndroidComposeRule<SettingsActivity>()
    private lateinit var keyboard: GlideKeyboardView
    private val output = mutableListOf<String>()
    private val actions = mutableListOf<String>()
    private var imeWindow: android.app.Dialog? = null

    @After fun closeImeWindow() {
        compose.runOnUiThread { imeWindow?.dismiss() }
    }

    private fun mount(width: Int = 360, height: Int = 232, owner: ImeComposeOwner? = null) {
        compose.runOnUiThread {
            val root = FrameLayout(compose.activity)
            owner?.attach(root)
            keyboard = GlideKeyboardView(compose.activity, object : GlideKeyboardView.Listener {
                override fun text(raw: String, japanese: Boolean) { output.add(raw) }
                override fun action(action: String) { actions.add(action) }
                override fun preview(route: String, result: String) {}
            })
            keyboard.setComposeRendering(true)
            root.addView(keyboard, FrameLayout.LayoutParams(width, height))
            compose.activity.setContentView(root)
            owner?.start()
        }
        compose.waitForIdle()
    }

    private fun event(action: Int, ids: IntArray, vararg positions: Float) {
        compose.runOnUiThread {
            val properties = Array(ids.size) { index ->
                MotionEvent.PointerProperties().also { it.id = ids[index]; it.toolType = MotionEvent.TOOL_TYPE_FINGER }
            }
            val coords = Array(ids.size) { index ->
                MotionEvent.PointerCoords().also {
                    it.x = positions[index * 2]; it.y = positions[index * 2 + 1]; it.pressure = 1f; it.size = 1f
                }
            }
            val event = MotionEvent.obtain(0, SystemClock.uptimeMillis(), action, ids.size, properties, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
            assertTrue(keyboard.dispatchTouchEvent(event))
            event.recycle()
        }
    }

    private fun tap(x: Float, y: Float) {
        event(MotionEvent.ACTION_DOWN, intArrayOf(7), x, y)
        event(MotionEvent.ACTION_UP, intArrayOf(7), x, y)
    }

    private fun capture(name: String, width: Int = 360, height: Int = 232) {
        compose.waitForIdle()
        compose.runOnIdle { SettingsActivityTest.capture(keyboard, name, width, height) }
    }

    @Test fun composeRendersLettersNumbersAndCustomSymbols() {
        mount()
        compose.onNodeWithText("A").assertExists()
        compose.onNodeWithText("全角").assertExists()
        capture("compose-keyboard-japanese")
        compose.runOnIdle { keyboard.resetMode(true) }
        compose.onNodeWithText("a").assertExists()
        tap(27f, 29f)
        compose.onNodeWithText("A").assertExists()
        capture("compose-keyboard-latin")
        compose.runOnIdle {
            keyboard.showNumbers()
            keyboard.setSymbolBoard(0, SymbolBoards.defaults(0).also { it[0] = "★" }, true)
        }
        compose.onNodeWithText("記号1").assertExists()
        tap(96f, 203f)
        compose.onNodeWithText("★").assertExists()
        tap(96f, 29f)
        compose.runOnIdle { assertEquals(listOf("★"), output) }
        compose.onNodeWithText("記号1").assertExists()
    }

    @Test fun composeChildDoesNotStealTwoFingerConsonantVowelInput() {
        mount()
        event(MotionEvent.ACTION_DOWN, intArrayOf(7), 180f, 29f)
        compose.runOnIdle { assertTrue(keyboard.visualState().keys()[1].active()) }
        event(MotionEvent.ACTION_POINTER_DOWN or (1 shl 8), intArrayOf(7, 12), 180f, 29f, 96f, 29f)
        compose.runOnIdle { assertEquals(listOf("ka"), output) }
        event(MotionEvent.ACTION_POINTER_UP, intArrayOf(7, 12), 180f, 29f, 96f, 29f)
        event(MotionEvent.ACTION_UP, intArrayOf(12), 96f, 29f)
        compose.runOnIdle { assertEquals(listOf("ka"), output) }
    }

    @Test fun glideTrailAndCancelUseTheOriginalMotionEvents() {
        mount()
        event(MotionEvent.ACTION_DOWN, intArrayOf(7), 180f, 29f)
        event(MotionEvent.ACTION_MOVE, intArrayOf(7), 96f, 29f)
        compose.runOnIdle { assertEquals(1, keyboard.visualState().paths().size) }
        capture("compose-keyboard-glide")
        event(MotionEvent.ACTION_UP, intArrayOf(7), 96f, 29f)
        compose.runOnIdle { assertEquals(listOf("ka"), output) }
        event(MotionEvent.ACTION_DOWN, intArrayOf(7), 180f, 29f)
        event(MotionEvent.ACTION_CANCEL, intArrayOf(7), 180f, 29f)
        compose.runOnIdle {
            assertEquals(listOf("ka"), output)
            assertTrue(keyboard.visualState().paths().isEmpty())
            assertTrue(keyboard.visualState().keys().none { it.active() })
        }
    }

    @Test fun switchingRendererCancelsHeldKeysAndReleasesComposition() {
        mount()
        event(MotionEvent.ACTION_DOWN, intArrayOf(7), 333f, 29f)
        compose.runOnIdle {
            keyboard.setComposeRendering(false)
            assertEquals(0, keyboard.childCount)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
            assertTrue(actions.isEmpty())
            keyboard.setComposeRendering(true)
            assertEquals(1, keyboard.childCount)
        }
        compose.onNodeWithText("A").assertExists()
        event(MotionEvent.ACTION_DOWN, intArrayOf(7), 27f, 29f)
        compose.runOnIdle { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout() + 1L)) }
        compose.runOnIdle { assertEquals(listOf("settings"), actions) }
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun floatingDarkKeyboardUsesMatchingVisualAndTouchWidths() {
        mount(280, 160)
        compose.runOnIdle {
            keyboard.setScaleTextToFit(true)
            keyboard.setSideWidths(25, 8)
            keyboard.setTypeface(ImeFonts.load(compose.activity, 1))
        }
        val bounds = compose.onNodeWithText("A").fetchSemanticsNode().boundsInRoot
        assertEquals(280f * (.25f + .67f / 6f), bounds.center.x, 1f)
        capture("compose-keyboard-floating-dark", 280, 160)
        tap(bounds.center.x, bounds.center.y)
        compose.runOnIdle { assertEquals(listOf("a"), output) }
    }

    @Test fun keyboardCanComposeWithServiceOwnersAndRestartAfterHiding() {
        val owner = ImeComposeOwner()
        mount(owner = owner)
        compose.runOnIdle {
            assertSame(owner, keyboard.findViewTreeLifecycleOwner())
            assertSame(owner, keyboard.findViewTreeSavedStateRegistryOwner())
            owner.stop()
            assertEquals(Lifecycle.State.CREATED, owner.lifecycle.currentState)
            keyboard.showNumbers()
            owner.start()
        }
        compose.onNodeWithText("記号1").assertExists()
        compose.runOnIdle {
            compose.activity.setContentView(FrameLayout(compose.activity))
            owner.destroy()
            assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
        }
    }

    @Test fun keyboardCanComposeInWindowWithoutActivityOwners() {
        compose.runOnUiThread {
            val owner = ImeComposeOwner()
            val content = FrameLayout(compose.activity)
            owner.attach(content)
            val dialog = android.app.Dialog(compose.activity)
            owner.attach(dialog.window!!.decorView)
            imeWindow = dialog
            dialog.setContentView(content)
            val keys = GlideKeyboardView(compose.activity, object : GlideKeyboardView.Listener {
                override fun text(raw: String, japanese: Boolean) {}
                override fun action(action: String) {}
                override fun preview(route: String, result: String) {}
            })
            keys.setComposeRendering(true)
            content.addView(keys, FrameLayout.LayoutParams(360, 232))
            dialog.show()
            owner.start()
        }
        compose.onNodeWithText("A").assertExists()
    }
}
