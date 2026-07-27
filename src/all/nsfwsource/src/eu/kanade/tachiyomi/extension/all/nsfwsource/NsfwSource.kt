package eu.kanade.tachiyomi.extension.all.nsfwsource

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * High-performance VPN-Free Tachimanga/Tachiyomi Extension for NSFW Sources.
 * Features built-in mirror domain switching and anti-blocking HTTP headers to bypass ISP blocks.
 */
class NsfwSource : ParsedHttpSource(), ConfigurableSource {

    override val name = "NSFW Source (VPN-Free)"
    override val lang = "all"
    override val supportsLatest = true

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0)
    }

    /**
     * Dynamic Base URL resolver allows user to select active mirrors or enter custom proxy URLs
     * without needing a VPN when primary domains are ISP-blocked.
     */
    override val baseUrl: String
        get() = preferences.getString(PREF_DOMAIN_KEY, DEFAULT_DOMAIN) ?: DEFAULT_DOMAIN

    override val client: OkHttpClient = network.client.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
                .header("User-Agent", preferences.getString(PREF_UA_KEY, DEFAULT_USER_AGENT) ?: DEFAULT_USER_AGENT)
                .header("Referer", "$baseUrl/")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cache-Control", "no-cache")

            chain.proceed(requestBuilder.build())
        }
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", preferences.getString(PREF_UA_KEY, DEFAULT_USER_AGENT) ?: DEFAULT_USER_AGENT)
        .add("Referer", "$baseUrl/")

    // =================================== Popular Manga ===================================

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/popular/page/$page/", headers)
    }

    override fun popularMangaSelector(): String = "div.post-item, div.manga-card, div.comic-item"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val titleElement = element.selectFirst("h3 a, .post-title a, .entry-title a") ?: element.selectFirst("a")
        title = titleElement?.text()?.trim() ?: "Unknown Title"
        url = titleElement?.attr("href")?.let { getUrlWithoutDomain(it) } ?: ""

        val imgElement = element.selectFirst("img")
        thumbnail_url = imgElement?.attr("data-src")
            ?.takeIf { it.isNotBlank() }
            ?: imgElement?.attr("src")
    }

    override fun popularMangaNextPageSelector(): String = "a.next, .pagination-next, a[rel=next]"

    // =================================== Latest Updates ===================================

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/latest/page/$page/", headers)
    }

    override fun latestUpdatesSelector(): String = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = popularMangaNextPageSelector()

    // ===================================== Search ========================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search/page/$page/".toHttpUrl().newBuilder()

        if (query.isNotBlank()) {
            url.addQueryParameter("s", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    if (filter.state != 0) {
                        url.addQueryParameter("genre", filter.toUriPart())
                    }
                }
                is SortFilter -> {
                    url.addQueryParameter("m_orderby", filter.toUriPart())
                }
                is StatusFilter -> {
                    if (filter.state != 0) {
                        url.addQueryParameter("status", filter.toUriPart())
                    }
                }
                else -> {}
            }
        }

        return GET(url.build().toString(), headers)
    }

    override fun searchMangaSelector(): String = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String = popularMangaNextPageSelector()

    // ================================== Manga Details ====================================

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1.entry-title, .post-title h1")?.text()?.trim() ?: ""
        author = document.select(".author-content a, .manga-author a").joinToString { it.text() }
        artist = document.select(".artist-content a, .manga-artist a").joinToString { it.text() }
        description = document.select(".description-summary, .manga-exporter-desc, .entry-content p")
            .text().trim()
        genre = document.select(".genres-content a, .manga-genres a").joinToString { it.text() }
        
        val statusText = document.select(".post-status .summary-content, .status-value").text().lowercase()
        status = when {
            statusText.contains("ongoing") -> SManga.ONGOING
            statusText.contains("completed") -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        val imgElement = document.selectFirst(".summary_image img, .manga-poster img")
        thumbnail_url = imgElement?.attr("data-src")?.takeIf { it.isNotBlank() }
            ?: imgElement?.attr("src")
    }

    // =================================== Chapter List =====================================

    override fun chapterListSelector(): String = "li.wp-manga-chapter, div.chapter-item"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val linkElement = element.selectFirst("a")
        name = linkElement?.text()?.trim() ?: "Chapter"
        url = linkElement?.attr("href")?.let { getUrlWithoutDomain(it) } ?: ""

        val dateStr = element.selectFirst("span.chapter-release-date, .date").text().trim()
        date_upload = parseDate(dateStr)
    }

    private fun parseDate(dateStr: String): Long {
        return runCatching {
            DATE_FORMATTER.parse(dateStr)?.time
        }.getOrNull() ?: 0L
    }

    // =================================== Page Extraction ==================================

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        val imgElements = document.select("div.page-break img, div.reading-content img, .chapter-video-frame img")

        imgElements.forEachIndexed { index, img ->
            val imageUrl = img.attr("data-src").takeIf { it.isNotBlank() }
                ?: img.attr("src").trim()
            
            if (imageUrl.isNotBlank()) {
                pages.add(Page(index, "", imageUrl))
            }
        }

        return pages
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    // ===================================== Filters =======================================

    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
        StatusFilter(),
        GenreFilter()
    )

    private class SortFilter : UriPartFilter(
        "Sort By",
        arrayOf(
            Pair("Relevance", ""),
            Pair("Latest Uploads", "latest"),
            Pair("Most Popular", "views"),
            Pair("Alphabetical", "alphabet")
        )
    )

    private class StatusFilter : UriPartFilter(
        "Status",
        arrayOf(
            Pair("All", ""),
            Pair("Ongoing", "ongoing"),
            Pair("Completed", "completed")
        )
    )

    private class GenreFilter : UriPartFilter(
        "Genres / Tags",
        arrayOf(
            Pair("All", ""),
            Pair("Full Color", "full-color"),
            Pair("Uncensored", "uncensored"),
            Pair("Doujinshi", "doujinshi"),
            Pair("Manga", "manga"),
            Pair("Manhwa", "manhwa"),
            Pair("Ahegao", "ahegao"),
            Pair("Romance", "romance")
        )
    )

    private open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    // ================================= Extension Preferences ===============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val domainPref = ListPreference(screen.context).apply {
            key = PREF_DOMAIN_KEY
            title = "Source Mirror / Domain (VPN-Free)"
            summary = "Select an unblocked mirror domain or direct proxy if your ISP blocks the main domain."
            entries = arrayOf("Primary Mirror (.to)", "Secondary Mirror (.net)", "Global Mirror (.org)", "Custom Domain")
            entryValues = arrayOf("https://nsfwsource.to", "https://nsfwsource.net", "https://nsfwsource.org", "CUSTOM")
            setDefaultValue(DEFAULT_DOMAIN)

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                preferences.edit().putString(PREF_DOMAIN_KEY, selected).apply()
                true
            }
        }

        val customDomainPref = EditTextPreference(screen.context).apply {
            key = PREF_CUSTOM_DOMAIN_KEY
            title = "Custom Base URL / Proxy"
            summary = "Enter custom mirror URL (e.g. https://my-custom-proxy.com) if preset domains are blocked."
            setDefaultValue("")

            setOnPreferenceChangeListener { _, newValue ->
                val customUrl = (newValue as String).trim().removeSuffix("/")
                if (customUrl.startsWith("http://") || customUrl.startsWith("https://")) {
                    preferences.edit().putString(PREF_DOMAIN_KEY, customUrl).apply()
                    true
                } else false
            }
        }

        val userAgentPref = EditTextPreference(screen.context).apply {
            key = PREF_UA_KEY
            title = "Custom User-Agent (Anti-Cloudflare)"
            summary = "Customize User-Agent header for Cloudflare/WAF bypass."
            setDefaultValue(DEFAULT_USER_AGENT)
        }

        screen.addPreference(domainPref)
        screen.addPreference(customDomainPref)
        screen.addPreference(userAgentPref)
    }

    companion object {
        private const val DEFAULT_DOMAIN = "https://nsfwsource.to"
        private const val DEFAULT_USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1"
        private const val PREF_DOMAIN_KEY = "pref_domain_key"
        private const val PREF_CUSTOM_DOMAIN_KEY = "pref_custom_domain_key"
        private const val PREF_UA_KEY = "pref_ua_key"

        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        }
    }
}
