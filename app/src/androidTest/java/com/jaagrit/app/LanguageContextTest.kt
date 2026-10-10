package com.jaagrit.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jaagrit.app.data.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class LanguageContextTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    private class TestLocalizedContextWrapper(
        base: Context,
        private val localizedConfiguration: Configuration
    ) : ContextWrapper(base) {
        override fun getResources() = baseContext.createConfigurationContext(localizedConfiguration).resources
    }

    @Test
    fun testActivityLookup_and_activityResultRegistryOwner_workWithWrappedContext() {
        val activity = composeTestRule.activity
        var launcherResolved = false
        var foundActivity: Activity? = null

        val localizedConfiguration = Configuration(activity.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("hi-IN"))
        }
        val wrappedContext = TestLocalizedContextWrapper(activity, localizedConfiguration)

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalContext provides wrappedContext,
                LocalActivityResultRegistryOwner provides activity
            ) {
                val context = LocalContext.current
                foundActivity = context.findActivity()

                // Verify rememberLauncherForActivityResult executes without throwing IllegalStateException
                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { /* no-op */ }
                launcherResolved = (launcher != null)
            }
        }

        composeTestRule.waitForIdle()

        assertTrue("rememberLauncherForActivityResult must resolve successfully", launcherResolved)
        assertNotNull("findActivity() must not return null with wrapped context", foundActivity)
        assertSame("findActivity() must return Activity instance", activity, foundActivity)
    }

    @Test
    fun testLanguageToggle_updatesImmediatelyWithoutRestart() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var initialActivity: MainActivity? = null

        scenario.onActivity { act ->
            initialActivity = act
            assertFalse("Activity must not be finishing", act.isFinishing)
        }

        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val settingsStore = SettingsStore(targetContext)

        // Toggle to English
        runBlocking {
            settingsStore.setAppLanguage("en")
            val lang = settingsStore.appLanguageFlow.first()
            assertEquals("en", lang)
        }

        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        scenario.onActivity { act ->
            assertSame("Activity must remain the exact same instance without restart", initialActivity, act)
            assertFalse("Activity must not be destroyed or finishing", act.isFinishing || act.isDestroyed)
        }

        // Toggle back to Hindi
        runBlocking {
            settingsStore.setAppLanguage("hi")
            val lang = settingsStore.appLanguageFlow.first()
            assertEquals("hi", lang)
        }

        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        scenario.onActivity { act ->
            assertSame("Activity must remain the exact same instance without restart", initialActivity, act)
            assertFalse("Activity must not be destroyed or finishing", act.isFinishing || act.isDestroyed)
        }

        scenario.close()
    }

    @Test
    fun testLocalizedResources_returnCorrectStringsForBothLocales() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

        val hiConfig = Configuration(targetContext.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("hi-IN"))
        }
        val hiContext = targetContext.createConfigurationContext(hiConfig)
        val hiString = hiContext.resources.getString(R.string.critical_headline)
        assertEquals("जागो!", hiString)

        val enConfig = Configuration(targetContext.resources.configuration).apply {
            setLocale(Locale.US)
        }
        val enContext = targetContext.createConfigurationContext(enConfig)
        val enString = enContext.resources.getString(R.string.critical_headline)
        assertEquals("WAKE UP!", enString)
    }
}
