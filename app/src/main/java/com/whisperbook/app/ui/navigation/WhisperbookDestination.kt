package com.whisperbook.app.ui.navigation

sealed class WhisperbookDestination(val route: String) {
    data object Welcome : WhisperbookDestination("welcome")
    data object Library : WhisperbookDestination("library")
    data object ImportBook : WhisperbookDestination("import")
    data object Processing : WhisperbookDestination("processing")
    data object NowPlaying : WhisperbookDestination("listen")
    data object BookDetails : WhisperbookDestination("book/{bookId}") {
        fun route(bookId: String = SAMPLE_BOOK_ID) = "book/$bookId"
    }
    data object VoiceCast : WhisperbookDestination("book/{bookId}/cast") {
        fun route(bookId: String = SAMPLE_BOOK_ID) = "book/$bookId/cast"
    }
    data object Settings : WhisperbookDestination("settings")
    data object CurrentChapter : WhisperbookDestination("book/{bookId}/chapter/{chapterId}") {
        fun route(bookId: String = SAMPLE_BOOK_ID, chapterId: String = SAMPLE_CHAPTER_ID) =
            "book/$bookId/chapter/$chapterId"
    }

    companion object {
        const val SAMPLE_BOOK_ID = "moonlit"
        const val SAMPLE_CHAPTER_ID = "chapter-7"

        val bottomBarRoutes: Set<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
            setOf(
                Welcome.route,
                Library.route,
                ImportBook.route,
                NowPlaying.route,
                BookDetails.route,
                Settings.route,
                CurrentChapter.route,
            )
        }
    }
}
