# Anubhav - Live it. Share it.

> **Create more. Share less.**

Anubhav is a calm, minimal social Android application designed for people who create things, explore places, learn, build, travel, and do meaningful activities. Rather than copying mainstream attention-maximizing networks, Anubhav is built to feel closer to a thoughtful creative journal—spacious, peaceful, lightweight, and intentional.

---

## Features

- **Calm, Chronological Feed (`Posts`)**: Newest posts first. No algorithmic ranking, no "For You" feeds, no engagement-farming loops.
- **Two Post Formats**:
  - **Text Posts**: Clean, readable reflections and updates.
  - **Photo Posts**: Single image with an optional caption.
- **Strict Like Privacy**:
  - Other users see only a calm heart indicator (`♡` / `♥`) without any numeric counts.
  - Only the post's author can see the total number of likes received on their own post (`♥ 18`).
- **Profile Exploration**:
  - Tap any creator's name or avatar to view their journey, including:
    - Display name & username
    - Bio
    - Location
    - **Currently working on** (projects, studies, crafts)
    - **Things I've done** (treks, skills learned, milestones)
    - Chronological history of their posts
- **Image Compression & Efficiency**:
  - Automatic downsampling and resizing to a maximum of 1280×1280 preserving aspect ratio.
  - Aggressive 70% JPEG compression and EXIF rotation correction.
  - Zero raw image bloat stored in the cloud.
- **Intentional 3-Tab Bottom Navigation**:
  1. `Posts` (Feed)
  2. `+` (Create)
  3. `Profile` (Current User Profile & Editing)

### Intentionally Excluded (By Design)
To protect focus, mental calm, and authentic sharing, the app deliberately **does not** include:
- ✗ Comments & thread debates
- ✗ Followers & following counters
- ✗ Push notifications
- ✗ Public like tallies
- ✗ Videos, Reels, & Stories
- ✗ Global search & Explore tabs
- ✗ Direct messaging
- ✗ Reposts, shares, & quotes
- ✗ Hashtags & trending lists
- ✗ Badges, streaks, & gamification

---

## Tech Stack & Architecture

- **Language**: Kotlin 2.2+
- **UI Framework**: Jetpack Compose with Material 3
- **Design Language**: Custom calm aesthetic (neutral warm palette, generous whitespace, line icons, subtle borders)
- **Architecture**: MVVM + Clean separation of concerns (Data, Domain, Presentation)
- **Reactive State**: Kotlin Coroutines & StateFlow
- **Image Loading**: Coil 2.7
- **Networking & Backend**: Supabase Kotlin Multiplatform SDK (`io.github.jan-tennert.supabase`)
  - **Auth**: Supabase GoTrue (Email & Password authentication, persistent session restoration)
  - **Database**: Supabase PostgreSQL with Row Level Security (RLS)
  - **Storage**: Supabase Storage buckets (`profile-images`, `post-images`)
  - **Engine**: Ktor 3.1 with OkHttp

```text
app/src/main/java/com/example/anubhav/
├── core/
│   ├── image/ImageCompressor.kt           # Downsampling, EXIF rotation & 70% JPEG compression
│   ├── supabase/SupabaseConfig.kt         # Project URL and anon key configuration
│   ├── supabase/SupabaseClientProvider.kt # Client singleton with Auth, Postgrest, Storage
│   └── utils/RelativeTimeFormatter.kt     # "just now", "2m", "1h", "Yesterday", "3d"
├── data/
│   ├── remote/dto/Dtos.kt                 # ProfileDto, PostDto, PostLikeDto, PostWithProfileDto
│   └── repository/
│       ├── AuthRepository.kt              # Login, register, logout, session status
│       ├── ProfileRepository.kt           # Profile fetch, upsert, username check, avatar upload
│       └── PostRepository.kt              # Feed pagination, text/image posts, delete, like toggle
├── domain/
│   └── model/Models.kt                    # UserProfile, PostWithAuthor, PostType
├── presentation/
│   ├── auth/                              # AuthScreen, AuthViewModel
│   ├── feed/                              # FeedScreen, FeedViewModel
│   ├── create/                            # CreatePostScreen, CreatePostViewModel
│   ├── profile/                           # ProfileScreen, UserProfileScreen, EditProfileScreen, ProfileViewModel
│   ├── components/                        # PostItem, CalmComponents, AvatarImage
│   └── navigation/                        # AppNavigation, Screen, BottomTab, MainScaffold
└── ui/theme/                              # Color, Theme, Type
```

---

## Supabase Setup Instructions

### 1. Create a Supabase Project
1. Log in to [Supabase](https://supabase.com) and create a new project.
2. In **Project Settings** -> **API**, copy:
   - **Project URL**
   - **anon / public key**

### 2. Run Database Schema & Storage Policies
Open the **SQL Editor** in your Supabase dashboard, paste the contents of [`supabase_schema.sql`](./supabase_schema.sql), and run it.

The SQL script creates:
- `profiles` table with unique username checks
- `posts` table with foreign keys and cascade delete
- `post_likes` table with unique `(post_id, user_id)` constraint
- Performance indexes on timestamps and user IDs
- Automatic `updated_at` trigger functions
- Row Level Security (RLS) policies for profiles, posts, and likes
- Storage buckets: `profile-images` and `post-images` (public read, authenticated user write/delete to their own folder)
- `get_email_by_username` RPC for secure username authentication

### 3. Configure Supabase Auth Redirect URLs
In your Supabase Dashboard under **Authentication** -> **URL Configuration**:
- Set **Site URL** or add **Redirect URLs**:
  - `anubhav://reset-password`
  - `anubhav://reset-password/**`

### 4. Configure Android App Credentials
Open [`app/src/main/java/com/example/anubhav/core/supabase/SupabaseConfig.kt`](./app/src/main/java/com/example/anubhav/core/supabase/SupabaseConfig.kt) and paste your credentials:

```kotlin
object SupabaseConfig {
    const val SUPABASE_URL: String = "https://<your-project-ref>.supabase.co"
    const val SUPABASE_ANON_KEY: String = "<your-anon-key>"
}
```

> **Security Note**: Never commit Supabase service-role keys or database passwords to the repository. The Android app strictly uses the client-side `anon` public key, guarded by PostgreSQL Row Level Security (RLS).

---

## Building and Running the App

### Requirements
- Android Studio Ladybug / Meerkat or later
- JDK 17
- Android SDK Platform 36+

### Command Line
```powershell
# Build the Debug APK
.\gradlew assembleDebug

# Run Unit Tests
.\gradlew test
```

The compiled APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## Verification & Testing Guide

### Two-User Verification Workflow:
1. **User A Registers**:
   - Register with email, password, display name (e.g. `Shailesh`), and username (e.g. `shailesh`).
   - Lands on the `Posts` feed.
2. **User A Creates Posts**:
   - Tap `+`.
   - Publish a text post: *"Finally finished building my first mechanical keyboard."*
   - Tap `+`. Select a photo from the gallery. Notice instant compression and preview. Add caption *"Himalayan summit at sunrise."* and tap **Post**.
3. **User B Logs In / Registers**:
   - Install or switch to User B account.
   - User B sees User A's posts in the chronological feed.
4. **Like Interaction & Privacy Check**:
   - User B taps the heart icon on User A's post.
   - The heart turns solid (`♥`).
   - Notice that User B **does not** see any like count!
   - Switch back to User A: User A sees `♥ 1` on their own post.
5. **Profile Exploration**:
   - User B taps User A's avatar or display name.
   - User A's profile opens showing their bio, *"Currently working on"*, *"Things I've done"*, and their published posts.
