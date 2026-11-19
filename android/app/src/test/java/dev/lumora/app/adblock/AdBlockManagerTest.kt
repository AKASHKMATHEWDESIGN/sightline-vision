package dev.lumora.app.adblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockManagerTest {

    @Test
    fun blocksKnownAdHostsAndSubdomains() {
        assertTrue(AdBlockManager.shouldBlock("https://securepubads.g.doubleclick.net/gampad/ads?x=1"))
        assertTrue(AdBlockManager.shouldBlock("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"))
        assertTrue(AdBlockManager.shouldBlock("https://www.googleadservices.com/pagead/conversion"))
        assertTrue(AdBlockManager.shouldBlock("https://www.google-analytics.com/analytics.js"))
        assertTrue(AdBlockManager.shouldBlock("https://imasdk.googleapis.com/js/sdkloader/ima3.js"))
    }

    @Test
    fun blocksYoutubeAdEndpoints() {
        assertTrue(AdBlockManager.shouldBlock("https://www.youtube.com/api/stats/ads?ver=2"))
        assertTrue(AdBlockManager.shouldBlock("https://m.youtube.com/pagead/viewthroughconversion/123"))
        assertTrue(AdBlockManager.shouldBlock("https://www.youtube.com/ptracking?ptk=abc"))
        assertTrue(AdBlockManager.shouldBlock("https://www.youtube.com/get_midroll_info?ei=1"))
        assertTrue(AdBlockManager.shouldBlock("https://music.youtube.com/youtubei/v1/log_event?alt=json"))
    }

    @Test
    fun allowsCoreYoutubeTraffic() {
        assertFalse(AdBlockManager.shouldBlock("https://m.youtube.com/"))
        assertFalse(AdBlockManager.shouldBlock("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertFalse(AdBlockManager.shouldBlock("https://music.youtube.com/youtubei/v1/player?key=1"))
        assertFalse(AdBlockManager.shouldBlock("https://s.youtube.com/api/stats/watchtime?ns=yt"))
        assertFalse(AdBlockManager.shouldBlock("https://i.ytimg.com/vi/abc/hqdefault.jpg"))
        assertFalse(AdBlockManager.shouldBlock("https://rr1---sn-abc.googlevideo.com/videoplayback?id=1"))
        assertFalse(AdBlockManager.shouldBlock("https://accounts.google.com/ServiceLogin?continue=x"))
        assertFalse(AdBlockManager.shouldBlock("https://www.gstatic.com/x.js"))
    }

    @Test
    fun ignoresMalformedOrNonHttpUrls() {
        assertFalse(AdBlockManager.shouldBlock("not a url"))
        assertFalse(AdBlockManager.shouldBlock("data:text/plain,hello"))
        assertFalse(AdBlockManager.shouldBlock("blob:https://m.youtube.com/123"))
    }
}
