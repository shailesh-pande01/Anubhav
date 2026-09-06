-- ==============================================================================
-- Anubhav - Live it. Share it.
-- Migration 001: Scalability, Performance & Integrity Optimizations
-- ==============================================================================

-- 1. CASE-INSENSITIVE USERNAME UNIQUENESS
-- Guarantees at the database constraint level that two accounts can never be created
-- with different casing (e.g. 'Shailesh' vs 'shailesh').
CREATE UNIQUE INDEX IF NOT EXISTS idx_profiles_username_lower_unique ON public.profiles (LOWER(username));

-- 2. COVERING INDEX FOR BOUNDED LIKES LOOKUP
-- Accelerates the bounded feed likes lookup: WHERE user_id = :currentUserId AND post_id IN (:postIds)
-- Enables PostgreSQL to perform an Index-Only Scan without reading table heap blocks.
CREATE INDEX IF NOT EXISTS idx_post_likes_user_post ON public.post_likes (user_id, post_id);

-- 3. SERVER-SIDE LIKE COUNTS AGGREGATION RPC
-- Eliminates N+1 client query loops. Takes an array of post UUIDs, aggregates like counts
-- on the database server, and returns only (post_id, like_count).
-- Zero raw like rows or user identity records are transferred over the network.
CREATE OR REPLACE FUNCTION public.get_post_like_counts(post_ids UUID[])
RETURNS TABLE (post_id UUID, like_count BIGINT)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT pl.post_id, count(*)::BIGINT AS like_count
    FROM public.post_likes pl
    WHERE pl.post_id = ANY(post_ids)
    GROUP BY pl.post_id;
$$;

-- Grant execution permission to anon and authenticated roles
GRANT EXECUTE ON FUNCTION public.get_post_like_counts(UUID[]) TO anon, authenticated;

-- ==============================================================================
-- CLEANUP / AUDIT HELPERS FOR LEGACY TIMESTAMPED AVATARS
-- ==============================================================================
-- The new application code writes to deterministic path: ${userId}/avatar.jpg
-- and automatically prunes the user's legacy avatar upon their next avatar change.
-- To inspect remaining legacy avatar URLs stored in profiles, run:
--
-- SELECT id, username, profile_image_url
-- FROM public.profiles
-- WHERE profile_image_url LIKE '%/avatar_%'
-- ORDER BY created_at DESC;
