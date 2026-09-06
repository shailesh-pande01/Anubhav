-- ==============================================================================
-- Anubhav - Live it. Share it.
-- Migration 005: Complete Admin Post Removal & Moderation Audit Trail Preservation
-- ==============================================================================

-- 1. PRESERVE POST ID IN MODERATION AUDIT TRAIL
-- Dropping the foreign key constraint from moderation_actions.post_id to posts.id
-- ensures that when a post is removed/deleted, the immutable UUID of the target post
-- is preserved in the audit log forever, answering: who removed what post, why, and when.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.table_constraints tc
        JOIN information_schema.constraint_column_usage ccu ON ccu.constraint_name = tc.constraint_name
        WHERE tc.table_name = 'moderation_actions'
          AND tc.constraint_type = 'FOREIGN KEY'
          AND ccu.column_name = 'id'
          AND ccu.table_name = 'posts'
          AND tc.constraint_name LIKE '%post_id%'
    ) THEN
        ALTER TABLE public.moderation_actions DROP CONSTRAINT IF EXISTS moderation_actions_post_id_fkey;
    END IF;
END $$;

-- 2. ATOMIC SERVER-SIDE ADMIN POST REMOVAL RPC
-- Executes complete removal in an atomic database transaction:
--   a) Enforces administrator authorization server-side
--   b) Checks whether post exists (gracefully handles race condition if already deleted)
--   c) Records moderation audit action in moderation_actions
--   d) Auto-resolves all pending/reviewing reports referencing this post
--   e) Deletes the posts row (cascades to post_likes)
--   f) Returns image_path for Supabase Storage cleanup
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
        -- Auto-resolve any remaining reports for this post so dashboard doesn't stay stale
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
    -- This cascades to post_likes (ON DELETE CASCADE)
    -- reports.post_id is safely handled (ON DELETE SET NULL)
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

-- Grant execution permission to authenticated users (admin check is inside function)
REVOKE ALL ON FUNCTION public.admin_remove_post(UUID, TEXT, UUID) FROM public, anon;
GRANT EXECUTE ON FUNCTION public.admin_remove_post(UUID, TEXT, UUID) TO authenticated;

-- 3. UPDATE ADMIN DASHBOARD STATS AGGREGATION
-- Posts removed count should reflect total POST_REMOVED actions from moderation_actions audit table,
-- ensuring dashboard metrics remain accurate even when posts are completely removed from the database.
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
