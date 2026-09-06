-- ==============================================================================
-- Anubhav - Live it. Share it.
-- Migration 002: Moderation, Reports, Admin Authorization & Audit System
-- ==============================================================================

-- 1. ADMIN USERS TABLE
CREATE TABLE IF NOT EXISTS public.admin_users (
    user_id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    role TEXT NOT NULL DEFAULT 'MODERATOR' CHECK (role IN ('SUPER_ADMIN', 'MODERATOR')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

ALTER TABLE public.admin_users ENABLE ROW LEVEL SECURITY;

-- Server-side helper to check if a user is an admin without table scans
CREATE OR REPLACE FUNCTION public.is_admin(p_user_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.admin_users WHERE user_id = p_user_id
    );
$$;

-- Secure RPC for authenticated user to check their own admin status
CREATE OR REPLACE FUNCTION public.check_is_current_user_admin()
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT public.is_admin(auth.uid());
$$;

GRANT EXECUTE ON FUNCTION public.check_is_current_user_admin() TO authenticated, anon;

-- Admin users RLS: Only admins can view admin list; no public insert/update/delete
DROP POLICY IF EXISTS "Admins can view admin_users" ON public.admin_users;
CREATE POLICY "Admins can view admin_users"
    ON public.admin_users FOR SELECT
    USING (public.is_admin(auth.uid()));

-- 2. USER MODERATION STATUS IN PROFILES
ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS account_status TEXT NOT NULL DEFAULT 'ACTIVE'
        CHECK (account_status IN ('ACTIVE', 'RESTRICTED', 'SUSPENDED', 'BANNED')),
    ADD COLUMN IF NOT EXISTS restricted_until TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS suspended_until TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS status_reason TEXT DEFAULT '';

-- Effective status evaluation (auto-resolves expired temporary restrictions/suspensions)
CREATE OR REPLACE FUNCTION public.get_effective_account_status(p_user_id UUID)
RETURNS TEXT
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_status TEXT;
    v_restricted_until TIMESTAMPTZ;
    v_suspended_until TIMESTAMPTZ;
BEGIN
    SELECT account_status, restricted_until, suspended_until
    INTO v_status, v_restricted_until, v_suspended_until
    FROM public.profiles
    WHERE id = p_user_id;

    IF v_status IS NULL THEN
        RETURN 'ACTIVE';
    END IF;

    IF v_status = 'BANNED' THEN
        RETURN 'BANNED';
    ELSIF v_status = 'SUSPENDED' THEN
        IF v_suspended_until IS NOT NULL AND v_suspended_until <= timezone('utc'::text, now()) THEN
            RETURN 'ACTIVE';
        ELSE
            RETURN 'SUSPENDED';
        END IF;
    ELSIF v_status = 'RESTRICTED' THEN
        IF v_restricted_until IS NOT NULL AND v_restricted_until <= timezone('utc'::text, now()) THEN
            RETURN 'ACTIVE';
        ELSE
            RETURN 'RESTRICTED';
        END IF;
    ELSE
        RETURN 'ACTIVE';
    END IF;
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_effective_account_status(UUID) TO anon, authenticated;

-- Protect moderation fields in profiles from client tampering
CREATE OR REPLACE FUNCTION public.protect_profile_moderation_fields()
RETURNS TRIGGER AS $$
BEGIN
    IF (OLD.account_status IS DISTINCT FROM NEW.account_status
        OR OLD.restricted_until IS DISTINCT FROM NEW.restricted_until
        OR OLD.suspended_until IS DISTINCT FROM NEW.suspended_until)
       AND NOT public.is_admin(auth.uid()) THEN
        -- Preserve old moderation values if caller is not an administrator
        NEW.account_status := OLD.account_status;
        NEW.restricted_until := OLD.restricted_until;
        NEW.suspended_until := OLD.suspended_until;
        NEW.status_reason := OLD.status_reason;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_protect_profile_moderation ON public.profiles;
CREATE TRIGGER trg_protect_profile_moderation
    BEFORE UPDATE ON public.profiles
    FOR EACH ROW
    EXECUTE FUNCTION public.protect_profile_moderation_fields();

-- Allow admins to update any profile (e.g. for moderating status)
DROP POLICY IF EXISTS "Admins can update any profile" ON public.profiles;
CREATE POLICY "Admins can update any profile"
    ON public.profiles FOR UPDATE
    USING (public.is_admin(auth.uid()));

-- 3. SOFT DELETION & MODERATION FIELDS IN POSTS
ALTER TABLE public.posts
    ADD COLUMN IF NOT EXISTS is_removed BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS removed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS removed_by UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS removal_reason TEXT DEFAULT '';

-- Ensure any existing rows have is_removed = false
UPDATE public.posts SET is_removed = false WHERE is_removed IS NULL;
ALTER TABLE public.posts ALTER COLUMN is_removed SET DEFAULT false;

CREATE INDEX IF NOT EXISTS idx_posts_is_removed ON public.posts(is_removed);

-- Update posts SELECT policy: only active posts visible, unless admin
DROP POLICY IF EXISTS "Posts are viewable by everyone" ON public.posts;
CREATE POLICY "Posts are viewable by everyone"
    ON public.posts FOR SELECT
    USING (COALESCE(is_removed, false) = false OR public.is_admin(auth.uid()));

-- Update posts INSERT policy: only ACTIVE accounts can post
DROP POLICY IF EXISTS "Authenticated users can insert their own posts" ON public.posts;
CREATE POLICY "Authenticated users can insert their own posts"
    ON public.posts FOR INSERT
    WITH CHECK (
        auth.uid() = user_id
        AND public.get_effective_account_status(auth.uid()) = 'ACTIVE'
    );

-- Update posts UPDATE policy: owner can edit if ACTIVE, or admin can update (for soft removal)
DROP POLICY IF EXISTS "Users can update their own posts" ON public.posts;
CREATE POLICY "Users can update their own posts"
    ON public.posts FOR UPDATE
    USING (
        (auth.uid() = user_id AND public.get_effective_account_status(auth.uid()) = 'ACTIVE')
        OR public.is_admin(auth.uid())
    );

-- Update posts DELETE policy: owner or admin can delete
DROP POLICY IF EXISTS "Users can delete their own posts" ON public.posts;
CREATE POLICY "Users can delete their own posts"
    ON public.posts FOR DELETE
    USING (auth.uid() = user_id OR public.is_admin(auth.uid()));

-- Post Likes: restricted/suspended/banned accounts cannot like posts
DROP POLICY IF EXISTS "Users can like posts as themselves" ON public.post_likes;
CREATE POLICY "Users can like posts as themselves"
    ON public.post_likes FOR INSERT
    WITH CHECK (
        auth.uid() = user_id
        AND public.get_effective_account_status(auth.uid()) = 'ACTIVE'
    );

-- 4. REPORTS TABLE
CREATE TABLE IF NOT EXISTS public.reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID REFERENCES public.posts(id) ON DELETE SET NULL,
    reporter_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    reported_user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    reason TEXT NOT NULL CHECK (reason IN ('Spam', 'Harassment', 'Inappropriate content', 'Hate speech', 'Violence', 'Other')),
    description TEXT DEFAULT '',
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'REVIEWING', 'RESOLVED', 'DISMISSED')),
    resolution TEXT DEFAULT '',
    reviewed_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    CONSTRAINT unique_post_reporter UNIQUE (post_id, reporter_id)
);

ALTER TABLE public.reports ENABLE ROW LEVEL SECURITY;

CREATE INDEX IF NOT EXISTS idx_reports_status ON public.reports(status);
CREATE INDEX IF NOT EXISTS idx_reports_post_id ON public.reports(post_id);
CREATE INDEX IF NOT EXISTS idx_reports_reported_user_id ON public.reports(reported_user_id);
CREATE INDEX IF NOT EXISTS idx_reports_reporter_id ON public.reports(reporter_id);
CREATE INDEX IF NOT EXISTS idx_reports_created_at ON public.reports(created_at DESC);

-- Reports RLS
-- Users can insert reports for other users' posts if they are not suspended/banned
DROP POLICY IF EXISTS "Users can create reports" ON public.reports;
CREATE POLICY "Users can create reports"
    ON public.reports FOR INSERT
    WITH CHECK (
        auth.uid() = reporter_id
        AND reporter_id != reported_user_id
        AND public.get_effective_account_status(auth.uid()) NOT IN ('SUSPENDED', 'BANNED')
    );

-- Reporters can view their own reports; admins can view all reports
DROP POLICY IF EXISTS "Users and admins can view reports" ON public.reports;
CREATE POLICY "Users and admins can view reports"
    ON public.reports FOR SELECT
    USING (
        auth.uid() = reporter_id
        OR public.is_admin(auth.uid())
    );

-- Only admins can update reports (status, resolution, reviewed_by, etc.)
DROP POLICY IF EXISTS "Admins can update reports" ON public.reports;
CREATE POLICY "Admins can update reports"
    ON public.reports FOR UPDATE
    USING (public.is_admin(auth.uid()));

-- Only admins can delete reports
DROP POLICY IF EXISTS "Admins can delete reports" ON public.reports;
CREATE POLICY "Admins can delete reports"
    ON public.reports FOR DELETE
    USING (public.is_admin(auth.uid()));

-- 5. MODERATION ACTIONS AUDIT TABLE
CREATE TABLE IF NOT EXISTS public.moderation_actions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    user_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    post_id UUID REFERENCES public.posts(id) ON DELETE SET NULL,
    report_id UUID REFERENCES public.reports(id) ON DELETE SET NULL,
    action TEXT NOT NULL CHECK (action IN ('WARNING', 'POST_REMOVED', 'USER_RESTRICTED', 'USER_SUSPENDED', 'USER_BANNED', 'REPORT_DISMISSED', 'STATUS_RESET')),
    reason TEXT NOT NULL DEFAULT '',
    duration_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

ALTER TABLE public.moderation_actions ENABLE ROW LEVEL SECURITY;

CREATE INDEX IF NOT EXISTS idx_moderation_actions_user_id ON public.moderation_actions(user_id);
CREATE INDEX IF NOT EXISTS idx_moderation_actions_admin_id ON public.moderation_actions(admin_id);
CREATE INDEX IF NOT EXISTS idx_moderation_actions_created_at ON public.moderation_actions(created_at DESC);

-- Moderation Actions RLS: Only admins can read or write
DROP POLICY IF EXISTS "Admins can insert moderation actions" ON public.moderation_actions;
CREATE POLICY "Admins can insert moderation actions"
    ON public.moderation_actions FOR INSERT
    WITH CHECK (
        auth.uid() = admin_id
        AND public.is_admin(auth.uid())
    );

DROP POLICY IF EXISTS "Admins can view moderation actions" ON public.moderation_actions;
CREATE POLICY "Admins can view moderation actions"
    ON public.moderation_actions FOR SELECT
    USING (public.is_admin(auth.uid()));

-- 6. ADMIN DASHBOARD AGGREGATION RPC
-- Delivers all high-level moderation statistics in a single round-trip query without N+1 loops.
CREATE OR REPLACE FUNCTION public.get_admin_dashboard_stats()
RETURNS TABLE (
    total_users BIGINT,
    total_posts BIGINT,
    pending_reports BIGINT,
    resolved_reports BIGINT,
    banned_users BIGINT,
    restricted_users BIGINT,
    suspended_users BIGINT,
    posts_removed BIGINT
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF NOT public.is_admin(auth.uid()) THEN
        RAISE EXCEPTION 'Access denied. Administrator privileges required.';
    END IF;

    RETURN QUERY
    SELECT
        (SELECT count(*)::BIGINT FROM public.profiles),
        (SELECT count(*)::BIGINT FROM public.posts WHERE is_removed = false),
        (SELECT count(*)::BIGINT FROM public.reports WHERE status = 'PENDING'),
        (SELECT count(*)::BIGINT FROM public.reports WHERE status = 'RESOLVED'),
        (SELECT count(*)::BIGINT FROM public.profiles WHERE account_status = 'BANNED'),
        (SELECT count(*)::BIGINT FROM public.profiles WHERE account_status = 'RESTRICTED'),
        (SELECT count(*)::BIGINT FROM public.profiles WHERE account_status = 'SUSPENDED'),
        (SELECT count(*)::BIGINT FROM public.posts WHERE is_removed = true);
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_admin_dashboard_stats() TO authenticated;

-- 7. STORAGE POLICIES: ALLOW ADMINS TO DELETE MODERATED POST IMAGES
DROP POLICY IF EXISTS "Admins can delete post images" ON storage.objects;
CREATE POLICY "Admins can delete post images"
    ON storage.objects FOR DELETE
    USING (
        bucket_id = 'post-images'
        AND public.is_admin(auth.uid())
    );
