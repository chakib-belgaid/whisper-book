package com.whisperbook.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import com.whisperbook.app.domain.model.Book
import com.whisperbook.app.domain.model.BookFormat
import com.whisperbook.app.domain.model.PreparationStage
import com.whisperbook.app.domain.model.PreparationState
import com.whisperbook.app.integration.WhisperbookUiSnapshot
import com.whisperbook.app.ui.navigation.WhisperbookDestination
import com.whisperbook.app.ui.screens.WhisperbookAppState
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProcessingScreenResponsiveTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun longTechnicalFilenameStaysInsideTheatreAndPrimaryActionWorks() {
        val title = "ba036_21_nouvelle_liste_acte_dress-hr-ns-30-jours-1"
        val appState = WhisperbookAppState().apply {
            synchronize(
                WhisperbookUiSnapshot(
                    books = listOf(testBook(title)),
                    selectedBook = testBook(title),
                    preparation = PreparationState(
                        stage = PreparationStage.PREPARING_AUDIO,
                        progressFraction = 0.85f,
                    ),
                ),
            )
        }

        composeRule.setContent {
            WhisperbookApp(
                appState = appState,
                navController = rememberNavController(),
                startDestination = WhisperbookDestination.Processing.route,
            )
        }

        val headerBounds = bounds("processing-header")
        val theatreBounds = bounds("processing-theatre")
        val plaqueBounds = bounds("processing-title-plaque")
        val titleBounds = bounds("processing-book-title")

        assertTrue("Header must finish before the theatre begins", headerBounds.bottom <= theatreBounds.top)
        assertTrue("Title plaque must stay inside the theatre", plaqueBounds.left >= theatreBounds.left)
        assertTrue("Title plaque must stay inside the theatre", plaqueBounds.right <= theatreBounds.right)
        assertTrue("Book title must stay inside its plaque", titleBounds.left >= plaqueBounds.left)
        assertTrue("Book title must stay inside its plaque", titleBounds.right <= plaqueBounds.right)
        assertTrue("Book title must stay inside its plaque", titleBounds.top >= plaqueBounds.top)
        assertTrue("Book title must stay inside its plaque", titleBounds.bottom <= plaqueBounds.bottom)

        captureScreenshot("processing")

        composeRule.onNodeWithTag("processing-primary-action").performScrollTo().performClick()
        composeRule.onNodeWithTag("now-playing-screen").assertExists()

        val playerPlaqueBounds = bounds("player-title-plaque")
        val playerTitleBounds = bounds("player-book-title")
        val chapterBadgeBounds = bounds("player-chapter-badge")
        val playerScreenBounds = bounds("now-playing-screen")
        val playerDeckBounds = bounds("player-control-deck")
        val backgroundStatusBounds = bounds("background-operation-status")
        assertTrue("Player title must stay inside its plaque", playerTitleBounds.left >= playerPlaqueBounds.left)
        assertTrue("Player title must stay inside its plaque", playerTitleBounds.right <= playerPlaqueBounds.right)
        assertTrue("Player title must stay inside its plaque", playerTitleBounds.top >= playerPlaqueBounds.top)
        assertTrue("Player title must stay inside its plaque", playerTitleBounds.bottom <= playerPlaqueBounds.bottom)
        assertTrue("Chapter badge must not cover the player title", chapterBadgeBounds.top >= playerTitleBounds.bottom)
        assertTrue(
            "Player controls must not stretch into an oversized empty panel",
            playerDeckBounds.height <= playerScreenBounds.height * 0.52f,
        )
        assertTrue(
            "Background preparation status must not cover player controls",
            backgroundStatusBounds.top >= playerDeckBounds.bottom,
        )
        captureScreenshot("now-playing")
    }

    private fun bounds(tag: String): Rect =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun captureScreenshot(prefix: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val metrics = instrumentation.targetContext.resources.displayMetrics
        val output = File(
            instrumentation.targetContext.getExternalFilesDir(null),
            "$prefix-${metrics.widthPixels}x${metrics.heightPixels}-${metrics.densityDpi}dpi.png",
        )
        FileOutputStream(output).use { stream ->
            composeRule.onRoot().captureToImage().asAndroidBitmap().compress(
                Bitmap.CompressFormat.PNG,
                100,
                stream,
            )
        }
    }

    private fun testBook(title: String) = Book(
        id = "responsive-test-book",
        title = title,
        author = null,
        format = BookFormat.PDF,
        sourceUri = null,
        privateSourcePath = null,
        coverPath = null,
        preparation = PreparationState(PreparationStage.PREPARING_AUDIO, progressFraction = 0.85f),
        currentChapterId = null,
        currentPassageId = null,
        progressFraction = 0f,
        lastOpenedAtEpochMs = 0L,
    )
}
