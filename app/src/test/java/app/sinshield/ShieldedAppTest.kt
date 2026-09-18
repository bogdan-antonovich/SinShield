package app.sinshield

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Signals arrive already lowercased and trimmed from the service's node walk, so the fixtures
 * here are written the same way.
 */
class ShieldedAppTest {

    @Test
    fun xProfileTabsIdentifyAwayFromFeed() {
        val signals = ScreenSignals(
            labels = listOf("posts, selected tab", "replies, tab", "media, tab"),
            viewIds = emptyList()
        )

        assertEquals(ShieldedScreenMode.PROFILE_OR_POST, ShieldedApp.X.screenMode(signals))
    }

    @Test
    fun xProfileContainerIdIdentifiesAwayFromFeed() {
        val signals = ScreenSignals(
            labels = listOf("home", "following"),
            viewIds = listOf("com.twitter.android:id/profile_header")
        )

        assertEquals(ShieldedScreenMode.PROFILE_OR_POST, ShieldedApp.X.screenMode(signals))
    }

    @Test
    fun xHomeFeedTabsDoNotLookLikeProfile() {
        val signals = ScreenSignals(
            labels = listOf("for you, selected tab", "following, tab", "home"),
            viewIds = listOf("com.twitter.android:id/navigation_home")
        )

        assertEquals(ShieldedScreenMode.FEED, ShieldedApp.X.screenMode(signals))
    }

    @Test
    fun instagramProfileHeaderIdentifiesAwayFromFeed() {
        val signals = ScreenSignals(
            labels = listOf("1,204 followers", "312 following", "edit profile"),
            viewIds = emptyList()
        )

        assertEquals(
            ShieldedScreenMode.PROFILE_OR_POST,
            ShieldedApp.INSTAGRAM.screenMode(signals)
        )
    }

    @Test
    fun instagramReelsViewerIsAwayFromFeed() {
        val signals = ScreenSignals(
            labels = listOf("home", "search", "reels"),
            viewIds = listOf("com.instagram.android:id/clips_viewer_view_pager")
        )

        assertEquals(ShieldedScreenMode.REELS, ShieldedApp.INSTAGRAM.screenMode(signals))
    }

    @Test
    fun instagramReelsCommentComposerDoesNotLookLikeCreation() {
        val signals = ScreenSignals(
            labels = listOf("home", "reels", "comment"),
            viewIds = listOf(
                "com.instagram.android:id/clips_viewer_view_pager",
                "com.instagram.android:id/comment_composer_input"
            )
        )

        assertEquals(ShieldedScreenMode.REELS, ShieldedApp.INSTAGRAM.screenMode(signals))
    }

    @Test
    fun instagramProfileOpenedReelDoesNotLookLikeCreation() {
        val signals = ScreenSignals(
            labels = listOf("like", "comment", "share"),
            viewIds = listOf(
                "com.instagram.android:id/post_viewer_container",
                "com.instagram.android:id/comment_composer_container",
                "com.instagram.android:id/media_picker_button"
            )
        )

        assertEquals(
            ShieldedScreenMode.PROFILE_OR_POST,
            ShieldedApp.INSTAGRAM.screenMode(signals)
        )
    }

    @Test
    fun instagramStoryViewerIsAwayFromFeed() {
        val signals = ScreenSignals(
            labels = listOf("send message"),
            viewIds = listOf("com.instagram.android:id/reel_viewer_media_container")
        )

        assertEquals(ShieldedScreenMode.STORY, ShieldedApp.INSTAGRAM.screenMode(signals))
    }

    /**
     * Instagram's bottom navigation carries "Reels" and its feed selector carries "Following" on
     * the home feed itself, so neither may be enough on its own to claim the user left the feed.
     */
    @Test
    fun instagramHomeFeedIsNotAwayFromFeed() {
        val signals = ScreenSignals(
            labels = listOf("home", "search", "reels", "following", "your story"),
            viewIds = listOf(
                "com.instagram.android:id/feed_tab",
                "com.instagram.android:id/clips_tab",
                "com.instagram.android:id/explore_tab",
                "com.instagram.android:id/creation_tab"
            )
        )

        assertEquals(ShieldedScreenMode.FEED, ShieldedApp.INSTAGRAM.screenMode(signals))
    }

    @Test
    fun instagramExploreGetsItsOwnRecoveryMode() {
        val signals = ScreenSignals(
            labels = listOf("search"),
            viewIds = listOf("com.instagram.android:id/explore_grid_container")
        )

        assertEquals(ShieldedScreenMode.EXPLORE, ShieldedApp.INSTAGRAM.screenMode(signals))
    }

    @Test
    fun instagramHighlightsUseStoryBehavior() {
        val signals = ScreenSignals(
            labels = listOf("send message"),
            viewIds = listOf("com.instagram.android:id/highlight_viewer_container")
        )

        assertEquals(ShieldedScreenMode.STORY, ShieldedApp.INSTAGRAM.screenMode(signals))
    }

    @Test
    fun instagramStandalonePostCanReturnToFeed() {
        val signals = ScreenSignals(
            labels = listOf("like", "comment", "share"),
            viewIds = listOf("com.instagram.android:id/post_viewer_container")
        )

        assertEquals(
            ShieldedScreenMode.PROFILE_OR_POST,
            ShieldedApp.INSTAGRAM.screenMode(signals)
        )
    }

    @Test
    fun instagramDirectAndLiveHaveDistinctModes() {
        assertEquals(
            ShieldedScreenMode.DIRECT_MESSAGE,
            ShieldedApp.INSTAGRAM.screenMode(
                ScreenSignals(emptyList(), listOf("com.instagram.android:id/direct_thread"))
            )
        )
        assertEquals(
            ShieldedScreenMode.LIVE,
            ShieldedApp.INSTAGRAM.screenMode(
                ScreenSignals(listOf("request to join"), emptyList())
            )
        )
    }

    @Test
    fun instagramCreationScreensAreExcludedEvenWhenTheyContainReelIds() {
        val signals = ScreenSignals(
            labels = listOf("new story"),
            viewIds = listOf(
                "com.instagram.android:id/camera_capture_container",
                "com.instagram.android:id/reel_viewer_preview"
            )
        )

        assertEquals(ShieldedScreenMode.CREATION, ShieldedApp.INSTAGRAM.screenMode(signals))
    }

    @Test
    fun facebookHomeFeedStaysFeedWithComposerLauncherAndNavigation() {
        val signals = ScreenSignals(
            labels = listOf(
                "home, selected tab",
                "what's on your mind?",
                "friends",
                "reels",
                "marketplace"
            ),
            viewIds = listOf(
                "com.facebook.katana:id/feed_tab",
                "com.facebook.katana:id/feed_composer_launcher"
            )
        )

        assertEquals(ShieldedScreenMode.FEED, ShieldedApp.FACEBOOK.screenMode(signals))
        assertTrue(ShieldedApp.FACEBOOK.creationEvidence(signals).isEmpty())
    }

    @Test
    fun facebookProfileAndFullscreenPhotoCanReturnToFeed() {
        assertEquals(
            ShieldedScreenMode.PROFILE_OR_POST,
            ShieldedApp.FACEBOOK.screenMode(
                ScreenSignals(
                    labels = listOf("posts, selected tab", "about, tab", "friends, tab"),
                    viewIds = emptyList()
                )
            )
        )
        assertEquals(
            ShieldedScreenMode.PROFILE_OR_POST,
            ShieldedApp.FACEBOOK.screenMode(
                ScreenSignals(
                    labels = listOf("like", "comment", "share"),
                    viewIds = listOf("com.facebook.katana:id/fullscreen_photo_viewer")
                )
            )
        )
    }

    @Test
    fun facebookStoriesUseProtectedSkip() {
        val signals = ScreenSignals(
            labels = listOf("reply to alex's story"),
            viewIds = listOf("com.facebook.katana:id/story_viewer_fragment")
        )

        assertEquals(ShieldedScreenMode.STORY, ShieldedApp.FACEBOOK.screenMode(signals))
    }

    @Test
    fun facebookReelsAndLegacyWatchUseVerticalVideoFlow() {
        assertEquals(
            ShieldedScreenMode.REELS,
            ShieldedApp.FACEBOOK.screenMode(
                ScreenSignals(
                    labels = listOf("like", "comment", "share"),
                    viewIds = listOf("com.facebook.katana:id/reels_feed_viewer")
                )
            )
        )
        assertEquals(
            ShieldedScreenMode.REELS,
            ShieldedApp.FACEBOOK.screenMode(
                ScreenSignals(
                    labels = listOf("videos"),
                    viewIds = listOf("com.facebook.katana:id/watch_and_browse_fragment")
                )
            )
        )
    }

    @Test
    fun facebookMessageAndLiveViewersHaveDistinctRecoveryActions() {
        assertEquals(
            ShieldedScreenMode.DIRECT_MESSAGE,
            ShieldedApp.FACEBOOK.screenMode(
                ScreenSignals(
                    labels = listOf("write a message"),
                    viewIds = listOf("com.facebook.katana:id/message_thread_view")
                )
            )
        )
        assertEquals(
            ShieldedScreenMode.LIVE,
            ShieldedApp.FACEBOOK.screenMode(
                ScreenSignals(
                    labels = listOf("live video", "send stars"),
                    viewIds = emptyList()
                )
            )
        )
    }

    @Test
    fun facebookMarketplaceListingUsesAwayFromFeedFlow() {
        val signals = ScreenSignals(
            labels = listOf("product photo", "message seller"),
            viewIds = listOf("com.facebook.katana:id/marketplace_product_detail")
        )

        assertEquals(
            ShieldedScreenMode.PROFILE_OR_POST,
            ShieldedApp.FACEBOOK.screenMode(signals)
        )
    }

    @Test
    fun facebookCreationScreensAreExcludedEvenWhenTheyContainViewerIds() {
        val signals = ScreenSignals(
            labels = listOf("create reel", "share reel"),
            viewIds = listOf(
                "com.facebook.katana:id/reels_creation_fragment",
                "com.facebook.katana:id/reel_viewer_preview"
            )
        )

        assertEquals(ShieldedScreenMode.CREATION, ShieldedApp.FACEBOOK.screenMode(signals))
        assertTrue(ShieldedApp.FACEBOOK.creationEvidence(signals).isNotEmpty())
    }

    @Test
    fun redditHomeFeedStaysFeedWithCreateAndInboxNavigation() {
        val signals = ScreenSignals(
            labels = listOf(
                "home, selected tab",
                "communities",
                "create",
                "inbox",
                "profile"
            ),
            viewIds = listOf(
                "com.reddit.frontpage:id/bottom_navigation_home",
                "com.reddit.frontpage:id/create_button"
            )
        )

        assertEquals(ShieldedScreenMode.FEED, ShieldedApp.REDDIT.screenMode(signals))
        assertTrue(ShieldedApp.REDDIT.creationEvidence(signals).isEmpty())
    }

    @Test
    fun redditCommunityPostProfileAndMediaUsePageFlow() {
        val awayViewIds = listOf(
            "com.reddit.frontpage:id/community_feed_container",
            "com.reddit.frontpage:id/post_detail_screen",
            "com.reddit.frontpage:id/profile_header",
            "com.reddit.frontpage:id/fullscreen_media_viewer"
        )

        awayViewIds.forEach { viewId ->
            assertEquals(
                ShieldedScreenMode.PROFILE_OR_POST,
                ShieldedApp.REDDIT.screenMode(
                    ScreenSignals(labels = listOf("home"), viewIds = listOf(viewId))
                )
            )
        }
    }

    @Test
    fun redditProfileTabsClassifyWithoutResourceIds() {
        val signals = ScreenSignals(
            labels = listOf("overview, selected tab", "posts, tab", "comments, tab"),
            viewIds = emptyList()
        )

        assertEquals(
            ShieldedScreenMode.PROFILE_OR_POST,
            ShieldedApp.REDDIT.screenMode(signals)
        )
    }

    @Test
    fun redditChatThreadReturnsToChatList() {
        val signals = ScreenSignals(
            labels = listOf("send a message"),
            viewIds = listOf("com.reddit.frontpage:id/chat_message_list")
        )

        assertEquals(
            ShieldedScreenMode.DIRECT_MESSAGE,
            ShieldedApp.REDDIT.screenMode(signals)
        )
    }

    @Test
    fun redditPostCreationIsExcludedButCommentComposerIsNot() {
        val creation = ScreenSignals(
            labels = listOf("create post", "choose a community", "add tags & flair"),
            viewIds = listOf("com.reddit.frontpage:id/post_title_input")
        )
        val viewedPost = ScreenSignals(
            labels = listOf("reply", "add a comment"),
            viewIds = listOf(
                "com.reddit.frontpage:id/post_detail_screen",
                "com.reddit.frontpage:id/comment_composer_input"
            )
        )

        assertEquals(ShieldedScreenMode.CREATION, ShieldedApp.REDDIT.screenMode(creation))
        assertTrue(ShieldedApp.REDDIT.creationEvidence(creation).isNotEmpty())
        assertEquals(
            ShieldedScreenMode.PROFILE_OR_POST,
            ShieldedApp.REDDIT.screenMode(viewedPost)
        )
        assertTrue(ShieldedApp.REDDIT.creationEvidence(viewedPost).isEmpty())
    }

    @Test
    fun redditSupportingEditorIdPairsWithPunctuatedCreationLabel() {
        val signals = ScreenSignals(
            labels = listOf("Add tags & flair"),
            viewIds = listOf("com.reddit.frontpage:id/post_title_input")
        )

        assertEquals(ShieldedScreenMode.CREATION, ShieldedApp.REDDIT.screenMode(signals))
        assertTrue(ShieldedApp.REDDIT.creationEvidence(signals).isNotEmpty())
    }

    @Test
    fun unknownRedditSurfaceStaysConservative() {
        assertEquals(
            ShieldedScreenMode.UNKNOWN,
            ShieldedApp.REDDIT.screenMode(ScreenSignals.EMPTY)
        )
    }

    @Test
    fun homeTabIsFoundByLabelOrViewId() {
        assertTrue(ShieldedApp.X.isHomeTab(listOf("home tab"), ""))
        assertTrue(ShieldedApp.X.isHomeTab(emptyList(), "com.twitter.android:id/navigation_home"))
        assertTrue(
            ShieldedApp.INSTAGRAM.isHomeTab(emptyList(), "com.instagram.android:id/feed_tab")
        )
        assertTrue(
            ShieldedApp.FACEBOOK.isHomeTab(emptyList(), "com.facebook.katana:id/feed_tab")
        )
        assertTrue(
            ShieldedApp.REDDIT.isHomeTab(
                emptyList(),
                "com.reddit.frontpage:id/bottom_navigation_home"
            )
        )
        assertFalse(ShieldedApp.INSTAGRAM.isHomeTab(listOf("search"), "id/search_tab"))
    }

    @Test
    fun recentsCardsMatchOnlyTheirOwnApp() {
        assertTrue(ShieldedApp.X.isRecentsCard(listOf("x")))
        assertTrue(ShieldedApp.X.isRecentsCard(listOf("x, app")))
        assertTrue(ShieldedApp.INSTAGRAM.isRecentsCard(listOf("instagram")))
        assertTrue(ShieldedApp.FACEBOOK.isRecentsCard(listOf("facebook, app")))
        assertTrue(ShieldedApp.REDDIT.isRecentsCard(listOf("reddit, app")))
        assertFalse(ShieldedApp.INSTAGRAM.isRecentsCard(listOf("x")))
        assertFalse(ShieldedApp.X.isRecentsCard(listOf("instagram")))
    }

    @Test
    fun onlyFullScreenBlockedAppsResolve() {
        assertTrue(ShieldedApp.forPackage("com.twitter.android") === ShieldedApp.X)
        assertTrue(ShieldedApp.forPackage("com.instagram.android") === ShieldedApp.INSTAGRAM)
        assertTrue(ShieldedApp.forPackage("com.facebook.katana") === ShieldedApp.FACEBOOK)
        assertTrue(ShieldedApp.forPackage("com.facebook.lite") === ShieldedApp.FACEBOOK_LITE)
        assertTrue(ShieldedApp.forPackage("com.reddit.frontpage") === ShieldedApp.REDDIT)
        assertTrue(ShieldedApp.forPackage(null) == null)
    }
}
