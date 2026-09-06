-- ==============================================================================
-- Anubhav - Live it. Share it.
-- Supabase PostgreSQL Schema & Security Policies (Consolidated)
-- ==============================================================================

-- 1. PROFILES TABLE
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    username TEXT UNIQUE NOT NULL,
    display_name TEXT NOT NULL,
    bio TEXT DEFAULT '',
    location TEXT DEFAULT '',
    currently_working_on TEXT DEFAULT '',
    things_ive_done TEXT DEFAULT '',
    profile_image_url TEXT,
    account_status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (account_status IN ('ACTIVE', 'RESTRICTED', 'SUSPENDED', 'BANNED')),
    restricted_until TIMESTAMPTZ,
    suspended_until TIMESTAMPTZ,
    status_reason TEXT DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    CONSTRAINT username_length CHECK (char_length(username) >= 3 AND char_length(username) <= 30),
    CONSTRAINT username_valid_chars CHECK (username ~ '^[a-zA-Z0-9_.]+$')
);

-- 2. POSTS TABLE
CREATE TABLE IF NOT EXISTS public.posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    post_type TEXT NOT NULL CHECK (post_type IN ('TEXT', 'IMAGE')),
    content TEXT NOT NULL DEFAULT '',
    image_path TEXT,
    is_removed BOOLEAN NOT NULL DEFAULT false,
    removed_at TIMESTAMPTZ,
    removed_by UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    removal_reason TEXT DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

-- 3. POST LIKES TABLE
CREATE TABLE IF NOT EXISTS public.post_likes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES public.posts(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    CONSTRAINT unique_post_user_like UNIQUE (post_id, user_id)
);

-- 4. ADMIN USERS TABLE
CREATE TABLE IF NOT EXISTS public.admin_users (
    user_id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    role TEXT NOT NULL DEFAULT 'MODERATOR' CHECK (role IN ('SUPER_ADMIN', 'MODERATOR')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

-- 5. REPORTS TABLE
CREATE TABLE IF NOT EXISTS public.reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID REFERENCES public.posts(id) ON DELETE SET NULL,
    reporter_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    reported_user_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    reason TEXT NOT NULL CHECK (reason IN ('Spam', 'Harassment', 'Inappropriate content', 'Hate speech', 'Violence', 'Other')),
    description TEXT DEFAULT '',
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'REVIEWING', 'RESOLVED', 'DISMISSED')),
    resolution TEXT DEFAULT '',
    reviewed_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    CONSTRAINT unique_post_reporter UNIQUE (post_id, reporter_id)
);

-- 6. MODERATION ACTIONS AUDIT TABLE
CREATE TABLE IF NOT EXISTS public.moderation_actions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    user_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    post_id UUID, -- Deliberately unconstrained so removed post UUID is preserved in audit history
    report_id UUID REFERENCES public.reports(id) ON DELETE SET NULL,
    action TEXT NOT NULL CHECK (action IN ('WARNING', 'POST_REMOVED', 'USER_RESTRICTED', 'USER_SUSPENDED', 'USER_BANNED', 'REPORT_DISMISSED', 'STATUS_RESET')),
    reason TEXT NOT NULL DEFAULT '',
    duration_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

-- 7. PERFORMANCE & INTEGRITY INDEXES
CREATE INDEX IF NOT EXISTS idx_posts_created_at ON public.posts(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_posts_user_id_created_at ON public.posts(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_posts_is_removed ON public.posts(is_removed);

CREATE INDEX IF NOT EXISTS idx_post_likes_post_id ON public.post_likes(post_id);
CREATE INDEX IF NOT EXISTS idx_post_likes_user_id ON public.post_likes(user_id);
CREATE INDEX IF NOT EXISTS idx_post_likes_user_post ON public.post_likes(user_id, post_id);

CREATE INDEX IF NOT EXISTS idx_profiles_username ON public.profiles(username);
CREATE UNIQUE INDEX IF NOT EXISTS idx_profiles_username_lower_unique ON public.profiles (LOWER(username));

CREATE INDEX IF NOT EXISTS idx_reports_status ON public.reports(status);
CREATE INDEX IF NOT EXISTS idx_reports_post_id ON public.reports(post_id);
CREATE INDEX IF NOT EXISTS idx_reports_reported_user_id ON public.reports(reported_user_id);
CREATE INDEX IF NOT EXISTS idx_reports_reporter_id ON public.reports(reporter_id);
CREATE INDEX IF NOT EXISTS idx_reports_created_at ON public.reports(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_moderation_actions_user_id ON public.moderation_actions(user_id);
CREATE INDEX IF NOT EXISTS idx_moderation_actions_admin_id ON public.moderation_actions(admin_id);
CREATE INDEX IF NOT EXISTS idx_moderation_actions_created_at ON public.moderation_actions(created_at DESC);

-- 8. AUTOMATIC UPDATED_AT TRIGGER
CREATE OR REPLACE FUNCTION public.handle_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = timezone('utc'::text, now());
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS set_profiles_updated_at ON public.profiles;
CREATE TRIGGER set_profiles_updated_at
    BEFORE UPDATE ON public.profiles
    FOR EACH ROW
    EXECUTE FUNCTION public.handle_updated_at();

DROP TRIGGER IF EXISTS set_posts_updated_at ON public.posts;
CREATE TRIGGER set_posts_updated_at
    BEFORE UPDATE ON public.posts
    FOR EACH ROW
    EXECUTE FUNCTION public.handle_updated_at();

-- 9. ADMIN & MODERATION HELPER FUNCTIONS
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

-- 10. ROW LEVEL SECURITY (RLS)
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.posts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.post_likes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.admin_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.moderation_actions ENABLE ROW LEVEL SECURITY;

-- Profiles Policies
DROP POLICY IF EXISTS "Public profiles are viewable by everyone" ON public.profiles;
CREATE POLICY "Public profiles are viewable by everyone"
    ON public.profiles FOR SELECT
    USING (true);

DROP POLICY IF EXISTS "Users can insert their own profile" ON public.profiles;
CREATE POLICY "Users can insert their own profile"
    ON public.profiles FOR INSERT
    WITH CHECK (auth.uid() = id);

DROP POLICY IF EXISTS "Users can update their own profile" ON public.profiles;
CREATE POLICY "Users can update their own profile"
    ON public.profiles FOR UPDATE
    USING (auth.uid() = id);

DROP POLICY IF EXISTS "Admins can update any profile" ON public.profiles;
CREATE POLICY "Admins can update any profile"
    ON public.profiles FOR UPDATE
    USING (public.is_admin(auth.uid()));

-- Posts Policies
DROP POLICY IF EXISTS "Posts are viewable by everyone" ON public.posts;
CREATE POLICY "Posts are viewable by everyone"
    ON public.posts FOR SELECT
    USING (COALESCE(is_removed, false) = false OR public.is_admin(auth.uid()));

DROP POLICY IF EXISTS "Authenticated users can insert their own posts" ON public.posts;
CREATE POLICY "Authenticated users can insert their own posts"
    ON public.posts FOR INSERT
    WITH CHECK (
        auth.uid() = user_id
        AND public.get_effective_account_status(auth.uid()) = 'ACTIVE'
    );

DROP POLICY IF EXISTS "Users can update their own posts" ON public.posts;
CREATE POLICY "Users can update their own posts"
    ON public.posts FOR UPDATE
    USING (
        (auth.uid() = user_id AND public.get_effective_account_status(auth.uid()) = 'ACTIVE')
        OR public.is_admin(auth.uid())
    );

DROP POLICY IF EXISTS "Users can delete their own posts" ON public.posts;
CREATE POLICY "Users can delete their own posts"
    ON public.posts FOR DELETE
    USING (auth.uid() = user_id OR public.is_admin(auth.uid()));

-- Post Likes Policies
DROP POLICY IF EXISTS "Post likes are viewable by authenticated users" ON public.post_likes;
CREATE POLICY "Post likes are viewable by authenticated users"
    ON public.post_likes FOR SELECT
    USING (auth.role() = 'authenticated');

DROP POLICY IF EXISTS "Users can like posts as themselves" ON public.post_likes;
CREATE POLICY "Users can like posts as themselves"
    ON public.post_likes FOR INSERT
    WITH CHECK (
        auth.uid() = user_id
        AND public.get_effective_account_status(auth.uid()) = 'ACTIVE'
    );

DROP POLICY IF EXISTS "Users can unlike their own likes" ON public.post_likes;
CREATE POLICY "Users can unlike their own likes"
    ON public.post_likes FOR DELETE
    USING (auth.uid() = user_id);

-- Admin Users Policies
DROP POLICY IF EXISTS "Admins can view admin_users" ON public.admin_users;
CREATE POLICY "Admins can view admin_users"
    ON public.admin_users FOR SELECT
    USING (public.is_admin(auth.uid()));

-- Reports Policies
DROP POLICY IF EXISTS "Users can create reports" ON public.reports;
CREATE POLICY "Users can create reports"
    ON public.reports FOR INSERT
    WITH CHECK (
        auth.uid() = reporter_id
        AND reporter_id != reported_user_id
        AND public.get_effective_account_status(auth.uid()) NOT IN ('SUSPENDED', 'BANNED')
    );

DROP POLICY IF EXISTS "Users and admins can view reports" ON public.reports;
CREATE POLICY "Users and admins can view reports"
    ON public.reports FOR SELECT
    USING (
        auth.uid() = reporter_id
        OR public.is_admin(auth.uid())
    );

DROP POLICY IF EXISTS "Admins can update reports" ON public.reports;
CREATE POLICY "Admins can update reports"
    ON public.reports FOR UPDATE
    USING (public.is_admin(auth.uid()));

DROP POLICY IF EXISTS "Admins can delete reports" ON public.reports;
CREATE POLICY "Admins can delete reports"
    ON public.reports FOR DELETE
    USING (public.is_admin(auth.uid()));

-- Moderation Actions Policies
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

-- 11. STORAGE BUCKETS & POLICIES SETUP
INSERT INTO storage.buckets (id, name, public)
VALUES ('profile-images', 'profile-images', true)
ON CONFLICT (id) DO UPDATE SET public = true;

INSERT INTO storage.buckets (id, name, public)
VALUES ('post-images', 'post-images', true)
ON CONFLICT (id) DO UPDATE SET public = true;

-- Profile Images Storage
DROP POLICY IF EXISTS "Public read profile images" ON storage.objects;
CREATE POLICY "Public read profile images"
    ON storage.objects FOR SELECT
    USING (bucket_id = 'profile-images');

DROP POLICY IF EXISTS "Users can upload their own profile image" ON storage.objects;
CREATE POLICY "Users can upload their own profile image"
    ON storage.objects FOR INSERT
    WITH CHECK (
        bucket_id = 'profile-images'
        AND auth.uid()::text = (storage.foldername(name))[1]
    );

DROP POLICY IF EXISTS "Users can update their own profile image" ON storage.objects;
CREATE POLICY "Users can update their own profile image"
    ON storage.objects FOR UPDATE
    USING (
        bucket_id = 'profile-images'
        AND auth.uid()::text = (storage.foldername(name))[1]
    );

DROP POLICY IF EXISTS "Users can delete their own profile image" ON storage.objects;
CREATE POLICY "Users can delete their own profile image"
    ON storage.objects FOR DELETE
    USING (
        bucket_id = 'profile-images'
        AND auth.uid()::text = (storage.foldername(name))[1]
    );

-- Post Images Storage
DROP POLICY IF EXISTS "Public read post images" ON storage.objects;
CREATE POLICY "Public read post images"
    ON storage.objects FOR SELECT
    USING (bucket_id = 'post-images');

DROP POLICY IF EXISTS "Users can upload their own post images" ON storage.objects;
CREATE POLICY "Users can upload their own post images"
    ON storage.objects FOR INSERT
    WITH CHECK (
        bucket_id = 'post-images'
        AND auth.uid()::text = (storage.foldername(name))[1]
    );

DROP POLICY IF EXISTS "Users can delete their own post images" ON storage.objects;
CREATE POLICY "Users can delete their own post images"
    ON storage.objects FOR DELETE
    USING (
        bucket_id = 'post-images'
        AND auth.uid()::text = (storage.foldername(name))[1]
    );

DROP POLICY IF EXISTS "Admins can delete post images" ON storage.objects;
CREATE POLICY "Admins can delete post images"
    ON storage.objects FOR DELETE
    USING (
        bucket_id = 'post-images'
        AND public.is_admin(auth.uid())
    );

-- 12. RPC FUNCTIONS
CREATE INDEX IF NOT EXISTS idx_profiles_username_lower ON public.profiles (LOWER(username));

CREATE OR REPLACE FUNCTION public.get_email_by_username(p_username TEXT)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_email TEXT;
BEGIN
    SELECT u.email INTO v_email
    FROM auth.users u
    JOIN public.profiles p ON p.id = u.id
    WHERE LOWER(p.username) = LOWER(TRIM(p_username))
    LIMIT 1;

    RETURN v_email;
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_email_by_username(TEXT) TO anon, authenticated;

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

GRANT EXECUTE ON FUNCTION public.get_post_like_counts(UUID[]) TO anon, authenticated;

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
        (SELECT count(*)::BIGINT FROM public.posts),
        (SELECT count(*)::BIGINT FROM public.reports WHERE status = 'PENDING'),
        (SELECT count(*)::BIGINT FROM public.reports WHERE status = 'RESOLVED'),
        (SELECT count(*)::BIGINT FROM public.profiles WHERE account_status = 'BANNED'),
        (SELECT count(*)::BIGINT FROM public.profiles WHERE account_status = 'RESTRICTED'),
        (SELECT count(*)::BIGINT FROM public.profiles WHERE account_status = 'SUSPENDED'),
        (SELECT count(*)::BIGINT FROM public.moderation_actions WHERE action = 'POST_REMOVED');
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_admin_dashboard_stats() TO authenticated;

-- Atomic server-side admin post removal RPC
CREATE OR REPLACE FUNCTION public.admin_remove_post(
    p_post_id UUID,
    p_reason TEXT DEFAULT '',
    p_report_id UUID DEFAULT NULL
)
RETURNS JSON
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth, pg_temp
AS $$
DECLARE
    v_admin_id UUID;
    v_author_id UUID;
    v_image_path TEXT;
    v_cleaned_reason TEXT;
    v_report_resolution TEXT;
BEGIN
    -- 1. Verify administrator authorization
    v_admin_id := auth.uid();
    IF v_admin_id IS NULL OR NOT public.is_admin(v_admin_id) THEN
        RAISE EXCEPTION 'Access denied. Administrator privileges required.';
    END IF;

    v_cleaned_reason := COALESCE(NULLIF(TRIM(p_reason), ''), 'Violated community standards');
    v_report_resolution := 'Post removed: ' || v_cleaned_reason;

    -- 2. Verify target post exists
    SELECT user_id, image_path
    INTO v_author_id, v_image_path
    FROM public.posts
    WHERE id = p_post_id;

    -- 3. Handle race condition: Post was already deleted by owner or another admin
    IF NOT FOUND THEN
        UPDATE public.reports
        SET status = 'RESOLVED',
            resolution = 'Post already removed',
            reviewed_by = v_admin_id,
            reviewed_at = timezone('utc'::text, now())
        WHERE (post_id = p_post_id OR id = p_report_id)
          AND status IN ('PENDING', 'REVIEWING');

        RETURN json_build_object(
            'success', true,
            'already_deleted', true,
            'image_path', NULL,
            'user_id', NULL
        );
    END IF;

    -- 4. Record moderation action audit log (preserves target_post_id)
    INSERT INTO public.moderation_actions (
        admin_id,
        user_id,
        post_id,
        report_id,
        action,
        reason,
        created_at
    ) VALUES (
        v_admin_id,
        v_author_id,
        p_post_id,
        p_report_id,
        'POST_REMOVED',
        v_cleaned_reason,
        timezone('utc'::text, now())
    );

    -- 5. Auto-resolve ALL pending/reviewing reports referencing this post
    UPDATE public.reports
    SET status = 'RESOLVED',
        resolution = v_report_resolution,
        reviewed_by = v_admin_id,
        reviewed_at = timezone('utc'::text, now())
    WHERE (post_id = p_post_id OR id = p_report_id)
      AND status IN ('PENDING', 'REVIEWING');

    -- 6. Perform hard deletion of the post database record
    DELETE FROM public.posts WHERE id = p_post_id;

    -- 7. Return payload for storage cleanup and client confirmation
    RETURN json_build_object(
        'success', true,
        'already_deleted', false,
        'image_path', v_image_path,
        'user_id', v_author_id
    );
END;
$$;

REVOKE ALL ON FUNCTION public.admin_remove_post(UUID, TEXT, UUID) FROM public, anon;
GRANT EXECUTE ON FUNCTION public.admin_remove_post(UUID, TEXT, UUID) TO authenticated;

-- Secure server-side account deletion RPC
CREATE OR REPLACE FUNCTION public.delete_user_account()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth, pg_temp
AS $$
DECLARE
    v_user_id UUID;
BEGIN
    -- 1. Identity validation strictly from authenticated session
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Authentication required. No active authenticated session.';
    END IF;

    -- 2. Auto-resolve pending reports for this user's content/account
    UPDATE public.reports
    SET status = 'RESOLVED',
        resolution = CASE 
            WHEN resolution IS NULL OR resolution = '' THEN 'Account deleted by user'
            ELSE resolution || ' (Account deleted by user)'
        END,
        reviewed_at = COALESCE(reviewed_at, timezone('utc'::text, now()))
    WHERE (reported_user_id = v_user_id OR post_id IN (SELECT id FROM public.posts WHERE user_id = v_user_id))
      AND status = 'PENDING';

    -- 3. Detach reporter_id from any reports filed by this user for user privacy
    UPDATE public.reports
    SET reporter_id = NULL
    WHERE reporter_id = v_user_id;

    -- 4. Clean up any remaining storage object records in the database for user's folders
    BEGIN
        DELETE FROM storage.objects
        WHERE bucket_id IN ('profile-images', 'post-images')
          AND (storage.foldername(name))[1] = v_user_id::text;
    EXCEPTION WHEN OTHERS THEN
        NULL;
    END;

    -- 5. Delete authentication user record from auth.users (cascades cleanly)
    DELETE FROM auth.users WHERE id = v_user_id;

END;
$$;

REVOKE ALL ON FUNCTION public.delete_user_account() FROM public, anon;
GRANT EXECUTE ON FUNCTION public.delete_user_account() TO authenticated;

