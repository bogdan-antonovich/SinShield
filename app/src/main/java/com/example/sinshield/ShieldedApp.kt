package com.example.sinshield

/** Text and resource-id evidence collected from one app window, lowercased and trimmed. */
internal data class ScreenSignals(
    val labels: List<String>,
    val viewIds: List<String>
) {
    companion object {
        val EMPTY = ScreenSignals(emptyList(), emptyList())
    }
}

/** The social-app surface currently visible and therefore the recovery actions that are safe. */
internal enum class ShieldedScreenMode {
    FEED,
    STORY,
    PROFILE_OR_POST,
    REELS,
    EXPLORE,
    DIRECT_MESSAGE,
    LIVE,
    CREATION,
    UNKNOWN
}

/**
 * An app whose feed media cannot be localized reliably enough to cover individual posts, so an
 * unsafe verdict is answered with the opaque full-screen block and its recovery actions rather
 * than per-image covers.
 *
 * Every field is app-specific evidence; the matching rules themselves are shared, so adding an app
 * means describing its navigation, not writing another detection path.
 */
internal data class ShieldedApp(
    val packageName: String,
    /** Shown inside the block's copy, e.g. "Close Instagram". */
    val displayName: String,
    /** Whether returning Home may fall back to clearing and relaunching only this app's task. */
    val restartAtLauncherFallback: Boolean = false,
    /** Whether a successful click on this app's explicit Home tab proves navigation succeeded. */
    val homeTabClickIsConclusive: Boolean = false,
    /** Resource-id fragments identifying the home/feed tab in the bottom navigation. */
    private val homeTabViewIdHints: List<String>,
    /** Labels the home/feed tab is announced with. */
    private val homeTabLabels: Set<String>,
    /** Titles the app's card carries in Recents. */
    private val recentsLabels: Set<String>,
    /**
     * Resource-id fragments that only exist on screens the user can be sent back to the feed
     * from. A single match is conclusive.
     */
    private val awayFromFeedViewIdHints: List<String>,
    /**
     * Labels found together on away-from-feed screens. Individually these are too weak — X shows
     * "Media" in a post's overflow and Instagram shows "Following" on the home feed — so
     * [AWAY_FROM_FEED_LABEL_QUORUM] of them must be visible at once.
     */
    private val awayFromFeedLabels: Set<String>
) {
    /** Resolves the current surface so the block can expose only actions that make sense there. */
    fun screenMode(signals: ScreenSignals): ShieldedScreenMode {
        return when {
            this === INSTAGRAM -> instagramScreenMode(signals)
            isFacebook -> facebookScreenMode(signals)
            this === REDDIT -> redditScreenMode(signals)
            matchesAwayFromFeed(signals) -> ShieldedScreenMode.PROFILE_OR_POST
            else -> ShieldedScreenMode.FEED
        }
    }

    private val isFacebook: Boolean
        get() = this === FACEBOOK || this === FACEBOOK_LITE

    private fun matchesAwayFromFeed(signals: ScreenSignals): Boolean {
        if (signals.viewIds.any { id -> awayFromFeedViewIdHints.any(id::contains) }) return true
        val matched = awayFromFeedLabels.count { label ->
            signals.labels.any { it.containsPhrase(label) }
        }
        return matched >= AWAY_FROM_FEED_LABEL_QUORUM
    }

    /**
     * Instagram reuses "reel" for Stories and "clips" for Reels. Match the strongest container
     * ids first, then fall back to profile-label evidence. Unknown screens deliberately receive
     * feed-safe actions rather than a destructive Back gesture.
     */
    private fun instagramScreenMode(signals: ScreenSignals): ShieldedScreenMode = when {
        instagramCreationEvidence(signals).isNotEmpty() -> ShieldedScreenMode.CREATION
        signals.hasAnyViewId(INSTAGRAM_LIVE_VIEW_ID_HINTS) ||
            signals.hasAnyLabel(INSTAGRAM_LIVE_LABELS) -> ShieldedScreenMode.LIVE
        signals.hasAnyViewId(INSTAGRAM_DIRECT_VIEW_ID_HINTS) ->
            ShieldedScreenMode.DIRECT_MESSAGE
        signals.hasAnyViewId(INSTAGRAM_STORY_VIEW_ID_HINTS) -> ShieldedScreenMode.STORY
        signals.hasAnyViewId(INSTAGRAM_REELS_VIEW_ID_HINTS) -> ShieldedScreenMode.REELS
        signals.hasAnyViewId(INSTAGRAM_EXPLORE_VIEW_ID_HINTS) -> ShieldedScreenMode.EXPLORE
        matchesAwayFromFeed(signals) -> ShieldedScreenMode.PROFILE_OR_POST
        signals.hasAnyViewId(homeTabViewIdHints) || signals.hasAnyLabel(homeTabLabels) ->
            ShieldedScreenMode.FEED
        else -> ShieldedScreenMode.UNKNOWN
    }

    /**
     * Facebook currently presents uploaded video and its former Watch/Video tab as Reels. Keep
     * legacy Watch ids in the same mode so both sides of that rollout get the vertical-video
     * recovery action. Viewer containers win over broad profile labels, while creation wins over
     * everything because camera and preview screens can contain those same viewer words.
     */
    private fun facebookScreenMode(signals: ScreenSignals): ShieldedScreenMode = when {
        facebookCreationEvidence(signals).isNotEmpty() -> ShieldedScreenMode.CREATION
        signals.hasAnyViewId(FACEBOOK_LIVE_VIEW_ID_HINTS) ||
            signals.hasLabelQuorum(FACEBOOK_LIVE_LABELS, FACEBOOK_SURFACE_LABEL_QUORUM) ->
            ShieldedScreenMode.LIVE
        signals.hasAnyViewId(FACEBOOK_DIRECT_VIEW_ID_HINTS) ->
            ShieldedScreenMode.DIRECT_MESSAGE
        signals.hasAnyViewId(FACEBOOK_STORY_VIEW_ID_HINTS) ||
            signals.hasLabelQuorum(FACEBOOK_STORY_LABELS, FACEBOOK_SURFACE_LABEL_QUORUM) ->
            ShieldedScreenMode.STORY
        signals.hasAnyViewId(FACEBOOK_REELS_VIEW_ID_HINTS) ||
            signals.hasLabelQuorum(FACEBOOK_REELS_LABELS, FACEBOOK_SURFACE_LABEL_QUORUM) ->
            ShieldedScreenMode.REELS
        matchesAwayFromFeed(signals) -> ShieldedScreenMode.PROFILE_OR_POST
        signals.hasAnyViewId(homeTabViewIdHints) || signals.hasAnyLabel(homeTabLabels) ->
            ShieldedScreenMode.FEED
        else -> ShieldedScreenMode.UNKNOWN
    }

    /**
     * Reddit keeps Home, Popular, News, and Latest as feed destinations, while communities, post
     * details, profiles, search results, and full-screen media are nested browsing surfaces. Its
     * Create button is visible from ordinary feeds, so creation must be established before the
     * broad page/feed evidence is considered.
     */
    private fun redditScreenMode(signals: ScreenSignals): ShieldedScreenMode = when {
        redditCreationEvidence(signals).isNotEmpty() -> ShieldedScreenMode.CREATION
        signals.hasAnyViewId(REDDIT_DIRECT_VIEW_ID_HINTS) ->
            ShieldedScreenMode.DIRECT_MESSAGE
        matchesAwayFromFeed(signals) -> ShieldedScreenMode.PROFILE_OR_POST
        signals.hasAnyViewId(homeTabViewIdHints) || signals.hasAnyLabel(homeTabLabels) ->
            ShieldedScreenMode.FEED
        else -> ShieldedScreenMode.UNKNOWN
    }

    /**
     * Creation is the only screen mode that can suppress a confirmed unsafe verdict, so it
     * requires stronger evidence than ordinary navigation classification. In particular, a Reel
     * or post may expose a comment-composer node; that must never make viewed content look like the
     * user's own draft.
     */
    fun creationEvidence(signals: ScreenSignals): List<String> =
        when {
            this === INSTAGRAM -> instagramCreationEvidence(signals)
            isFacebook -> facebookCreationEvidence(signals)
            this === REDDIT -> redditCreationEvidence(signals)
            else -> emptyList()
        }

    private fun instagramCreationEvidence(signals: ScreenSignals): List<String> {
        val strongViewIds = signals.viewIds.filter { id ->
            INSTAGRAM_STRONG_CREATION_VIEW_ID_HINTS.any(id::contains)
        }
        if (strongViewIds.isNotEmpty()) return strongViewIds.map { "viewId=$it" }

        val matchedLabels = INSTAGRAM_CREATION_LABELS.filter { label ->
            signals.labels.any { it.containsPhrase(label) }
        }
        if (matchedLabels.size >= INSTAGRAM_CREATION_LABEL_QUORUM) {
            return matchedLabels.map { "label=$it" }
        }

        val supportingViewIds = signals.viewIds.filter { id ->
            INSTAGRAM_SUPPORTING_CREATION_VIEW_ID_HINTS.any(id::contains)
        }
        return if (supportingViewIds.isNotEmpty() && matchedLabels.isNotEmpty()) {
            supportingViewIds.map { "viewId=$it" } + matchedLabels.map { "label=$it" }
        } else {
            emptyList()
        }
    }

    /**
     * A lone "What's on your mind?" launcher is part of Facebook's feed and is intentionally not
     * creation evidence. Suppression requires a dedicated composer/camera/picker container, two
     * creation labels, or a weaker composer id accompanied by a creation label.
     */
    private fun facebookCreationEvidence(signals: ScreenSignals): List<String> {
        val strongViewIds = signals.viewIds.filter { id ->
            FACEBOOK_STRONG_CREATION_VIEW_ID_HINTS.any(id::contains)
        }
        if (strongViewIds.isNotEmpty()) return strongViewIds.map { "viewId=$it" }

        val matchedLabels = FACEBOOK_CREATION_LABELS.filter { label ->
            signals.labels.any { it.containsPhrase(label) }
        }
        if (matchedLabels.size >= FACEBOOK_CREATION_LABEL_QUORUM) {
            return matchedLabels.map { "label=$it" }
        }

        val supportingViewIds = signals.viewIds.filter { id ->
            FACEBOOK_SUPPORTING_CREATION_VIEW_ID_HINTS.any(id::contains)
        }
        return if (supportingViewIds.isNotEmpty() && matchedLabels.isNotEmpty()) {
            supportingViewIds.map { "viewId=$it" } + matchedLabels.map { "label=$it" }
        } else {
            emptyList()
        }
    }

    /**
     * A feed-level Create button and a post's comment composer are not enough to exempt a screen.
     * The dedicated post editor/picker ids are conclusive; otherwise Reddit needs either two
     * composer labels or a supporting editor id paired with one of those labels.
     */
    private fun redditCreationEvidence(signals: ScreenSignals): List<String> {
        val strongViewIds = signals.viewIds.filter { id ->
            REDDIT_STRONG_CREATION_VIEW_ID_HINTS.any(id::contains)
        }
        if (strongViewIds.isNotEmpty()) return strongViewIds.map { "viewId=$it" }

        val matchedLabels = REDDIT_CREATION_LABELS.filter { label ->
            signals.labels.any { it.containsPhrase(label) }
        }
        if (matchedLabels.size >= REDDIT_CREATION_LABEL_QUORUM) {
            return matchedLabels.map { "label=$it" }
        }

        val supportingViewIds = signals.viewIds.filter { id ->
            REDDIT_SUPPORTING_CREATION_VIEW_ID_HINTS.any(id::contains)
        }
        return if (supportingViewIds.isNotEmpty() && matchedLabels.isNotEmpty()) {
            supportingViewIds.map { "viewId=$it" } + matchedLabels.map { "label=$it" }
        } else {
            emptyList()
        }
    }

    fun isHomeTab(labels: List<String>, viewId: String): Boolean =
        labels.any { candidate -> homeTabLabels.any { candidate.startsWithLabel(it) } } ||
            homeTabViewIdHints.any(viewId::contains)

    fun isRecentsCard(labels: List<String>): Boolean =
        labels.any { candidate -> recentsLabels.any { candidate.startsWithLabel(it) } }

    /**
     * Evidence matching is deliberately loose: the counters that identify an Instagram profile
     * arrive as "1,204 followers", so the word has to be found anywhere in the label. Whole words
     * only — otherwise "follow" would match inside "followers" and inflate the quorum.
     */
    private fun String.containsPhrase(expected: String): Boolean {
        val candidateWords = normalizedWords()
        val expectedWords = expected.normalizedWords()
        return expectedWords.isNotEmpty() && " $candidateWords ".contains(" $expectedWords ")
    }

    private fun String.normalizedWords(): String =
        map { if (it.isLetterOrDigit()) it.lowercaseChar() else ' ' }
            .joinToString("")
            .split(Regex("\\s+"))
            .filter(String::isNotEmpty)
            .joinToString(" ")

    private fun ScreenSignals.hasAnyViewId(hints: Collection<String>): Boolean =
        viewIds.any { id -> hints.any(id::contains) }

    private fun ScreenSignals.hasAnyLabel(labels: Collection<String>): Boolean =
        this.labels.any { candidate -> labels.any { candidate.containsPhrase(it) } }

    private fun ScreenSignals.hasLabelQuorum(labels: Collection<String>, quorum: Int): Boolean =
        labels.count { expected -> this.labels.any { it.containsPhrase(expected) } } >= quorum

    /**
     * Node matching stays strict. These results are acted on — a click on the home tab, a swipe
     * that closes a task in Recents — so matching the wrong app's card is worse than matching
     * nothing. Only the decoration accessibility adds to a label is tolerated: "Posts,
     * selected tab", "Home tab", "X, app".
     */
    private fun String.startsWithLabel(expected: String): Boolean =
        this == expected ||
            startsWith("$expected,") ||
            startsWith("$expected tab") ||
            startsWith("$expected. tab")

    companion object {
        private const val AWAY_FROM_FEED_LABEL_QUORUM = 2
        private const val INSTAGRAM_CREATION_LABEL_QUORUM = 2
        private const val FACEBOOK_CREATION_LABEL_QUORUM = 2
        private const val FACEBOOK_SURFACE_LABEL_QUORUM = 2
        private const val REDDIT_CREATION_LABEL_QUORUM = 2

        private val INSTAGRAM_STORY_VIEW_ID_HINTS = setOf(
            "reel_viewer",
            "story_viewer",
            "highlight_viewer"
        )
        private val INSTAGRAM_REELS_VIEW_ID_HINTS = setOf(
            "clips_viewer",
            "reels_viewer"
        )
        private val INSTAGRAM_EXPLORE_VIEW_ID_HINTS = setOf(
            "explore_grid",
            "search_grid",
            "explore_fragment"
        )
        private val INSTAGRAM_DIRECT_VIEW_ID_HINTS = setOf(
            "direct_inbox",
            "direct_thread",
            "direct_message",
            "inbox_container",
            "message_list",
            "thread_view"
        )
        private val INSTAGRAM_LIVE_VIEW_ID_HINTS = setOf(
            "live_viewer",
            "live_video",
            "broadcast_viewer"
        )
        private val INSTAGRAM_STRONG_CREATION_VIEW_ID_HINTS = setOf(
            "camera_capture",
            "camera_shutter",
            "gallery_picker",
            "edit_media",
            "photo_editor",
            "caption_input"
        )
        private val INSTAGRAM_SUPPORTING_CREATION_VIEW_ID_HINTS = setOf(
            "media_picker",
            "composer_"
        )
        private val INSTAGRAM_CREATION_LABELS = setOf(
            "new post",
            "new story",
            "edit photo",
            "add caption"
        )
        private val INSTAGRAM_LIVE_LABELS = setOf(
            "live video",
            "request to join"
        )

        private val FACEBOOK_STORY_VIEW_ID_HINTS = setOf(
            "story_viewer",
            "stories_viewer",
            "story_fullscreen",
            "story_media_viewer",
            "story_viewer_fragment"
        )
        private val FACEBOOK_REELS_VIEW_ID_HINTS = setOf(
            "reel_viewer",
            "reels_viewer",
            "reels_feed",
            "reel_video",
            // Pre-2025 and transitional Facebook builds still expose Watch/Video ids.
            "watch_and_browse",
            "watch_feed",
            "video_home"
        )
        private val FACEBOOK_DIRECT_VIEW_ID_HINTS = setOf(
            "messages_inbox",
            "message_thread",
            "messenger_thread",
            "thread_view"
        )
        private val FACEBOOK_LIVE_VIEW_ID_HINTS = setOf(
            "live_video_viewer",
            "live_broadcast_viewer",
            "live_stream_viewer"
        )
        private val FACEBOOK_STRONG_CREATION_VIEW_ID_HINTS = setOf(
            "composer_activity",
            "composer_fragment",
            "post_composer",
            "reel_creation",
            "reels_creation",
            "story_creation",
            "camera_capture",
            "media_picker",
            "photo_picker"
        )
        private val FACEBOOK_SUPPORTING_CREATION_VIEW_ID_HINTS = setOf(
            "composer_",
            "camera_",
            "picker_"
        )
        private val FACEBOOK_CREATION_LABELS = setOf(
            "create post",
            "add to your post",
            "create reel",
            "share reel",
            "create story",
            "share to story",
            "edit photo",
            "say something about this"
        )
        private val FACEBOOK_STORY_LABELS = setOf(
            "reply to story",
            "pause story",
            "next story",
            "previous story"
        )
        private val FACEBOOK_REELS_LABELS = setOf(
            "reel audio",
            "use audio",
            "remix this reel"
        )
        private val FACEBOOK_LIVE_LABELS = setOf(
            "live video",
            "send stars",
            "leave live video"
        )

        private val REDDIT_DIRECT_VIEW_ID_HINTS = setOf(
            "chat_conversation",
            "chat_message_list",
            "chat_thread",
            "conversation_screen",
            "message_thread"
        )
        private val REDDIT_STRONG_CREATION_VIEW_ID_HINTS = setOf(
            "create_post_screen",
            "create_post_container",
            "post_creation",
            "post_editor",
            "post_submit",
            "media_post_creation",
            "post_media_picker",
            "post_image_picker",
            "post_video_picker",
            "edit_post_screen"
        )
        private val REDDIT_SUPPORTING_CREATION_VIEW_ID_HINTS = setOf(
            "post_composer",
            "post_title_input",
            "post_body_input",
            "community_picker",
            "post_type_picker",
            "flair_selector"
        )
        private val REDDIT_CREATION_LABELS = setOf(
            "create post",
            "post to reddit",
            "add title",
            "add body text",
            "choose a community",
            "add tags and flair",
            "add tags & flair",
            "mark as 18+",
            "mark as nsfw",
            "save draft"
        )

        val X = ShieldedApp(
            packageName = "com.twitter.android",
            displayName = "X",
            homeTabViewIdHints = listOf("/home", "navigation_home"),
            homeTabLabels = setOf("home"),
            recentsLabels = setOf("x", "x app"),
            awayFromFeedViewIdHints = listOf(
                "profile_header",
                "profile_tabs",
                "user_profile",
                "profile_page"
            ),
            awayFromFeedLabels = setOf(
                "posts",
                "replies",
                "highlights",
                "articles",
                "media",
                "likes"
            )
        )

        /**
         * Instagram names Stories "reel" and Reels "clips" internally, so both viewer ids are
         * listed. Its bottom navigation keeps a literal "Reels" entry on the home feed, which is
         * why no tab name appears among the label evidence — only profile-header text does.
         */
        val INSTAGRAM = ShieldedApp(
            packageName = "com.instagram.android",
            displayName = "Instagram",
            // Instagram changes bottom-navigation destinations inside the same accessibility
            // window and can briefly expose no feed ids after the click. The Home target itself
            // is app-specific and strong enough to restore the pre-Facebook recovery behavior.
            homeTabClickIsConclusive = true,
            homeTabViewIdHints = listOf(
                "feed_tab",
                "home_tab",
                "tab_bar_home",
                "navigation_home",
                "/home"
            ),
            homeTabLabels = setOf("home"),
            recentsLabels = setOf("instagram"),
            awayFromFeedViewIdHints = listOf(
                "profile_header",
                "user_detail",
                "post_viewer",
                "permalink"
            ),
            awayFromFeedLabels = setOf(
                "followers",
                "following",
                "edit profile",
                "share profile",
                "tagged"
            )
        )

        /**
         * Facebook media can appear in Feed, profiles, Pages, Groups, Marketplace, full-screen
         * photo viewers, Stories, Reels/legacy Watch, Live, and in-app message threads. The broad
         * profile labels require a quorum so the Home feed's navigation shortcuts cannot classify
         * an ordinary post as an away-from-feed surface.
         */
        val FACEBOOK = ShieldedApp(
            packageName = "com.facebook.katana",
            displayName = "Facebook",
            restartAtLauncherFallback = true,
            homeTabViewIdHints = listOf(
                "feed_tab",
                "news_feed",
                "tab_home",
                "home_tab",
                "navigation_home",
                "/home"
            ),
            homeTabLabels = setOf("home", "news feed"),
            recentsLabels = setOf("facebook"),
            awayFromFeedViewIdHints = listOf(
                "profile_header",
                "profile_fragment",
                "profile_timeline",
                "timeline_fragment",
                "photo_viewer",
                "fullscreen_photo",
                "media_viewer",
                "permalink",
                "group_feed",
                "page_feed",
                "marketplace_product",
                "listing_detail"
            ),
            awayFromFeedLabels = setOf(
                "posts",
                "about",
                "friends",
                "photos"
            )
        )

        /** Facebook Lite exposes the same surfaces under a different Android package. */
        val FACEBOOK_LITE = FACEBOOK.copy(packageName = "com.facebook.lite")

        /**
         * Reddit media appears in the Home/Popular/News/Latest feeds, community feeds, post and
         * comment detail, profiles, search, full-screen viewers, and chat. Unknown Reddit screens
         * stay conservative; a launcher restart is available when the Home target cannot be
         * exposed or positively verified through accessibility.
         */
        val REDDIT = ShieldedApp(
            packageName = "com.reddit.frontpage",
            displayName = "Reddit",
            restartAtLauncherFallback = true,
            homeTabViewIdHints = listOf(
                "bottom_nav_home",
                "bottom_navigation_home",
                "navigation_home",
                "nav_home",
                "home_feed",
                "/home"
            ),
            homeTabLabels = setOf("home"),
            recentsLabels = setOf("reddit"),
            awayFromFeedViewIdHints = listOf(
                "community_detail",
                "community_header",
                "community_feed",
                "subreddit",
                "profile_header",
                "profile_screen",
                "user_profile",
                "post_detail",
                "comments_page",
                "comments_screen",
                "comment_tree",
                "media_viewer",
                "image_viewer",
                "video_viewer",
                "fullscreen_media",
                "gallery_viewer",
                "search_results"
            ),
            awayFromFeedLabels = setOf(
                "overview",
                "posts",
                "comments",
                "about",
                "submitted",
                "saved",
                "hidden",
                "upvoted",
                "downvoted"
            )
        )

        private val byPackage = listOf(X, INSTAGRAM, FACEBOOK, FACEBOOK_LITE, REDDIT)
            .associateBy(ShieldedApp::packageName)

        fun forPackage(packageName: String?): ShieldedApp? = byPackage[packageName]
    }
}
