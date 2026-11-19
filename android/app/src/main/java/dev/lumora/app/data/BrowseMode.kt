package dev.lumora.app.data

/** The two faces of Lumora. Each mode has its own home URL and accent palette. */
enum class BrowseMode(val mobileUrl: String, val desktopUrl: String) {
    Video(mobileUrl = "https://m.youtube.com", desktopUrl = "https://www.youtube.com"),
    Music(mobileUrl = "https://music.youtube.com", desktopUrl = "https://music.youtube.com");

    fun homeUrl(desktop: Boolean): String = if (desktop) desktopUrl else mobileUrl

    companion object {
        /** Picks the mode a given URL belongs to (e.g. shared links). */
        fun forUrl(url: String): BrowseMode =
            if (url.contains("music.youtube.com")) Music else Video
    }
}
