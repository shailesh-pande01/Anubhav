-- ==============================================================================
-- Anubhav - Live it. Share it.
-- Migration 004: Production-Ready Secure Account Deletion & Moderation Integrity
-- ==============================================================================

-- 1. PRESERVE MODERATION & REPORTS INTEGRITY
-- Allow reporter_id and reported_user_id to be NULL when an account is deleted,
-- so reports are anonymized rather than cascade-deleted, preserving platform safety records.
DO $$
BEGIN
    -- reporter_id column nullability
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' AND table_name = 'reports' AND column_name = 'reporter_id' AND is_nullable = 'NO'
    ) THEN
        ALTER TABLE public.reports ALTER COLUMN reporter_id DROP NOT NULL;
    END IF;

    -- reported_user_id column nullability
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' AND table_name = 'reports' AND column_name = 'reported_user_id' AND is_nullable = 'NO'
    ) THEN
        ALTER TABLE public.reports ALTER COLUMN reported_user_id DROP NOT NULL;
    END IF;

    -- Adjust foreign keys on reports to ON DELETE SET NULL
    ALTER TABLE public.reports DROP CONSTRAINT IF EXISTS reports_reporter_id_fkey;
    ALTER TABLE public.reports ADD CONSTRAINT reports_reporter_id_fkey
        FOREIGN KEY (reporter_id) REFERENCES public.profiles(id) ON DELETE SET NULL;

    ALTER TABLE public.reports DROP CONSTRAINT IF EXISTS reports_reported_user_id_fkey;
    ALTER TABLE public.reports ADD CONSTRAINT reports_reported_user_id_fkey
        FOREIGN KEY (reported_user_id) REFERENCES public.profiles(id) ON DELETE SET NULL;
END $$;

-- 2. PRESERVE MODERATION AUDIT TRAIL
-- Ensure moderation_actions are never cascade-deleted if an admin account is deleted.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' AND table_name = 'moderation_actions' AND column_name = 'admin_id' AND is_nullable = 'NO'
    ) THEN
        ALTER TABLE public.moderation_actions ALTER COLUMN admin_id DROP NOT NULL;
    END IF;

    ALTER TABLE public.moderation_actions DROP CONSTRAINT IF EXISTS moderation_actions_admin_id_fkey;
    ALTER TABLE public.moderation_actions ADD CONSTRAINT moderation_actions_admin_id_fkey
        FOREIGN KEY (admin_id) REFERENCES public.profiles(id) ON DELETE SET NULL;
END $$;

-- 3. SECURE SERVER-SIDE ACCOUNT DELETION RPC
-- Validates caller identity exclusively from the authenticated JWT session (auth.uid()).
-- Never trusts client-provided user IDs.
-- Automatically handles associated posts, likes, reports, moderation records, and auth user.
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
        -- Non-fatal if storage.objects is handled externally or restricted
        NULL;
    END;

    -- 5. Delete authentication user record from auth.users.
    -- This triggers CASCADE deletion on:
    --   - public.profiles (ON DELETE CASCADE)
    --     -> public.posts (ON DELETE CASCADE)
    --     -> public.post_likes (ON DELETE CASCADE)
    --     -> public.admin_users (ON DELETE CASCADE)
    -- Foreign keys with ON DELETE SET NULL safely preserve:
    --   - public.reports (post_id, reporter_id, reported_user_id set to NULL)
    --   - public.moderation_actions (user_id, post_id, report_id, admin_id set to NULL)
    DELETE FROM auth.users WHERE id = v_user_id;

END;
$$;

-- Grant execution permission strictly to authenticated users
REVOKE ALL ON FUNCTION public.delete_user_account() FROM public, anon;
GRANT EXECUTE ON FUNCTION public.delete_user_account() TO authenticated;
