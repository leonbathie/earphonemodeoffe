package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class EarphoneModeScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun speakerMode_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = true) {
        EarphoneModeScreen(
          isSpeakerMode = true,
          muteOnDisconnect = true,
          onSpeakerModeChanged = {},
          onMuteSettingChanged = {},
          onTestAudio = {},
          onResetAudio = {},
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/speaker_mode.png")
  }

  @Test
  fun earphoneMode_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = true) {
        EarphoneModeScreen(
          isSpeakerMode = false,
          muteOnDisconnect = false,
          onSpeakerModeChanged = {},
          onMuteSettingChanged = {},
          onTestAudio = {},
          onResetAudio = {},
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/earphone_mode.png")
  }
}
