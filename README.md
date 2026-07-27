# Tachimanga VPN-Free NSFW Extension Project

This project contains a fully configured, production-ready **Tachimanga / Tachiyomi Extension** designed specifically for adult (NSFW) content with built-in mechanisms to operate without requiring a VPN.

---

## Key Features

1. **VPN-Free Mirror Switching**:
   - Built-in preference menu in Tachimanga allowing you to switch domains/mirrors (`.to`, `.net`, `.org`) or enter a custom mirror/proxy URL directly inside Tachimanga settings if an ISP blocks a default domain.
2. **Anti-Blocking Interceptors**:
   - Automatic HTTP request header injection (`User-Agent`, `Referer`, `Accept-Language`, `Cache-Control`) to bypass Cloudflare and WAF bot filters.
3. **Full Content Parsing**:
   - Popular Manga / Doujinshi browsing.
   - Latest Release feed.
   - Deep Search with Filters (Sort by Relevance/Views, Status, Tag selection like Uncensored, Doujinshi, Full Color).
   - Manga metadata, chapter lists, and high-res image page parsing.

---

## Project Structure

```text
tachimanga-nsfw-extension/
├── build.gradle.kts
├── settings.gradle.kts
├── common.gradle
├── gradle.properties
├── index.min.json                  # Extension repository index for Tachimanga
└── src/
    └── all/
        └── nsfwsource/
            ├── build.gradle
            └── src/
                └── eu/
                    └── kanade/
                        └── tachiyomi/
                            └── extension/
                                └── all/
                                    └── nsfwsource/
                                        └── NsfwSource.kt
```

---

## How to Build & Install in Tachimanga

### Step 1: Compile the Extension APK
In your terminal (or Android Studio), run:
```bash
./gradlew assembleRelease
```
This generates the APK at:
`src/all/nsfwsource/build/outputs/apk/release/tachiyomi-all.nsfwsource-v1.4.1.apk`

### Step 2: Add Repository to Tachimanga
1. Upload the project (including `index.min.json` and the built APK) to GitHub.
2. Open **Tachimanga** on your iOS device.
3. Navigate to **Settings** > **Browse** > **Extension Repositories**.
4. Tap **Add Repository** and enter your raw GitHub JSON URL:
   `https://raw.githubusercontent.com/<YOUR_USERNAME>/<REPO_NAME>/main/index.min.json`
5. Tap **Install** on **NSFW Source (VPN-Free)** under the Extensions tab.

---

## Configuring VPN-Free Access inside Tachimanga

If your ISP blocks the primary domain in your region:
1. Open **Tachimanga** > **Browse** > **NSFW Source**.
2. Tap the **Gear / Settings Icon** in the top right.
3. Select **Source Mirror / Domain**.
4. Choose an alternative domain (`.net`, `.org`) or select **Custom Domain** and enter a working mirror proxy URL.
