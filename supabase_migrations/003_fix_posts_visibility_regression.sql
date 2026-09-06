-- ==============================================================================
-- Anubhav - Live it. Share it.
-- Migration 003: Fix Existing Posts Visibility Regression
-- ==============================================================================

-- 1. DATA MIGRATION: Backfill existing posts where is_removed is NULL
-- In PostgreSQL, `is_removed = false` evaluates to NULL (false) when is_removed IS NULL.
-- This ensures all legacy posts created prior to the moderation migration remain visible.
UPDATE public.posts
SET is_removed = false
WHERE is_removed IS NULL;

-- 2. SCHEMA HARDENING: Ensure is_removed has DEFAULT false and NOT NULL constraint
ALTER TABLE public.posts
    ALTER COLUMN is_removed SET DEFAULT false;

ALTER TABLE public.posts
    ALTER COLUMN is_removed SET NOT NULL;

-- Ensure removal_reason is not null
UPDATE public.posts
SET removal_reason = ''
WHERE removal_reason IS NULL;

ALTER TABLE public.posts
    ALTER COLUMN removal_reason SET DEFAULT '';

-- 3. RESOLVE AMBIGUOUS FOREIGN KEY RELATIONSHIP (PGRST200 PREVENTION)
-- If `removed_by` references `public.profiles(id)`, PostgREST detects TWO foreign keys
-- between `posts` and `profiles` (`user_id` and `removed_by`), causing queries like `profiles(*)`
-- to fail with: "Could not embed because more than one relationship was found for 'posts' and 'profiles'".
-- Administrative users are auth users, so `removed_by` should reference `auth.users(id)`.
-- This guarantees `user_id -> profiles(id)` is the single, unambiguous relationship.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.table_constraints tc
        JOIN information_schema.constraint_column_usage ccu ON ccu.constraint_name = tc.constraint_name
        WHERE tc.table_name = 'posts'
          AND tc.constraint_type = 'FOREIGN KEY'
          AND ccu.column_name = 'id'
          AND ccu.table_name = 'profiles'
          AND tc.constraint_name LIKE '%removed_by%'
    ) THEN
        ALTER TABLE public.posts DROP CONSTRAINT IF EXISTS posts_removed_by_fkey;
        ALTER TABLE public.posts ADD CONSTRAINT posts_removed_by_fkey
            FOREIGN KEY (removed_by) REFERENCES auth.users(id) ON DELETE SET NULL;
    END IF;
END $$;

-- 4. RLS POLICY FIX: Use COALESCE to guarantee legacy rows (if any) or NULLs are visible
-- Normal users see all non-removed posts (COALESCE(is_removed, false) = false).
-- Administrators see all posts including removed ones for moderation audits.
DROP POLICY IF EXISTS "Posts are viewable by everyone" ON public.posts;
CREATE POLICY "Posts are viewable by everyone"
    ON public.posts FOR SELECT
    USING (
        COALESCE(is_removed, false) = false
        OR public.is_admin(auth.uid())
    );

-- Verify and ensure owner can update their own posts
DROP POLICY IF EXISTS "Users can update their own posts" ON public.posts;
CREATE POLICY "Users can update their own posts"
    ON public.posts FOR UPDATE
    USING (
        (auth.uid() = user_id AND public.get_effective_account_status(auth.uid()) = 'ACTIVE')
        OR public.is_admin(auth.uid())
    );

-- Verify and ensure owner or admin can delete posts
DROP POLICY IF EXISTS "Users can delete their own posts" ON public.posts;
CREATE POLICY "Users can delete their own posts"
    ON public.posts FOR DELETE
    USING (
        auth.uid() = user_id
        OR public.is_admin(auth.uid())
    );

-- Refresh index for high-performance feed filtering
CREATE INDEX IF NOT EXISTS idx_posts_is_removed_created_at
    ON public.posts(is_removed, created_at DESC);
