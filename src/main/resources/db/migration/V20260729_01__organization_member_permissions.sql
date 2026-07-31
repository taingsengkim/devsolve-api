CREATE TABLE IF NOT EXISTS public.organization_member_permissions (
    organization_member_id UUID NOT NULL,
    permission VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_organization_member_permissions
        PRIMARY KEY (organization_member_id, permission),
    CONSTRAINT fk_organization_member_permissions_member
        FOREIGN KEY (organization_member_id)
        REFERENCES public.organization_members (id)
        ON DELETE CASCADE,
    CONSTRAINT chk_organization_member_permission
        CHECK (permission IN (
            'VIEW_PROGRAMS',
            'CREATE_PROGRAM',
            'EDIT_PROGRAM',
            'MANAGE_PROGRAM_STATE',
            'VIEW_REPORTS',
            'TRIAGE_REPORTS',
            'MANAGE_DISCLOSURE',
            'AWARD_REWARDS'
        ))
);

CREATE INDEX IF NOT EXISTS idx_organization_member_permissions_permission
    ON public.organization_member_permissions (permission);

INSERT INTO public.organization_member_permissions (
    organization_member_id,
    permission
)
SELECT
    member.id,
    role_permission.permission
FROM public.organization_members member
CROSS JOIN LATERAL unnest(
    CASE lower(member.role::text)
        WHEN 'manager' THEN ARRAY[
            'VIEW_PROGRAMS',
            'CREATE_PROGRAM',
            'EDIT_PROGRAM',
            'MANAGE_PROGRAM_STATE',
            'VIEW_REPORTS',
            'TRIAGE_REPORTS',
            'MANAGE_DISCLOSURE',
            'AWARD_REWARDS'
        ]::VARCHAR[]
        WHEN 'member' THEN ARRAY[
            'VIEW_PROGRAMS',
            'VIEW_REPORTS',
            'TRIAGE_REPORTS',
            'MANAGE_DISCLOSURE',
            'AWARD_REWARDS'
        ]::VARCHAR[]
        ELSE ARRAY[
            'VIEW_PROGRAMS',
            'VIEW_REPORTS'
        ]::VARCHAR[]
    END
) AS role_permission(permission)
ON CONFLICT (organization_member_id, permission) DO NOTHING;
