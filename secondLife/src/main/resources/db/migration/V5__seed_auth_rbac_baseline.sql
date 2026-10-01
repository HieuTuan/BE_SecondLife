-- Baseline authorization must exist even when optional account seeders are disabled.
INSERT INTO roles (code, name, description) VALUES
    ('BUYER', 'Buyer', 'Standard marketplace buyer'),
    ('SELLER', 'Seller', 'Verified marketplace seller'),
    ('INSPECTOR', 'Inspector', 'Inspection specialist'),
    ('STAFF', 'Staff', 'Operations staff'),
    ('ADMIN', 'Administrator', 'System administrator'),
    ('INSPECTION_CENTER', 'Inspection Center', 'Legacy inspection partner role')
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (code, name, description) VALUES
    ('PROFILE_READ_SELF', 'Read Own Profile', 'Read personal profile'),
    ('PROFILE_UPDATE_SELF', 'Update Own Profile', 'Update personal profile'),
    ('PASSWORD_CHANGE_SELF', 'Change Own Password', 'Change account password'),
    ('SELLER_VERIFICATION_SUBMIT', 'Submit Seller Verification', 'Submit seller verification'),
    ('SELLER_VERIFICATION_READ_SELF', 'Read Own Seller Verification', 'Read own verification'),
    ('USER_READ_ANY', 'Read Any User', 'Read user accounts'),
    ('USER_STATUS_UPDATE', 'Update User Status', 'Update account status'),
    ('SELLER_VERIFICATION_READ_ANY', 'Read Any Seller Verification', 'Read seller verifications'),
    ('SELLER_VERIFICATION_REVIEW', 'Review Seller Verification', 'Review seller verifications'),
    ('POST_REVIEW', 'Review Posts', 'Approve or reject posts'),
    ('INSPECTION_CENTER_ACCOUNT_MANAGE', 'Manage Inspection Center Accounts', 'Provision inspection partners'),
    ('ROLE_READ', 'Read Roles', 'Read roles and permissions'),
    ('LISTING_CREATE_SELF', 'Create Own Listing', 'Create own listings'),
    ('LISTING_PUBLISH_SELF', 'Publish Own Listing', 'Publish own listings'),
    ('INSPECTION_REPORT_SUBMIT', 'Submit Inspection Report', 'Submit inspection reports'),
    ('STAFF_LISTING_REVIEW', 'Review Listings', 'Review marketplace listings'),
    ('STAFF_FRAUD_REVIEW', 'Review Fraud', 'Review fraud signals'),
    ('STAFF_DISPUTE_REVIEW', 'Review Disputes', 'Review disputes'),
    ('STAFF_PAYOUT_REVIEW', 'Review Payouts', 'Review payouts'),
    ('STAFF_PAYOUT_HOLD', 'Hold Payouts', 'Hold payouts'),
    ('STAFF_USER_WARN', 'Warn Users', 'Warn users'),
    ('STAFF_USER_TEMP_RESTRICT', 'Temporarily Restrict Users', 'Temporarily restrict users'),
    ('ADMIN_STAFF_MANAGE', 'Manage Staff', 'Manage staff accounts'),
    ('ADMIN_RBAC_MANAGE', 'Manage RBAC', 'Manage roles and permissions'),
    ('ADMIN_PRICING_MANAGE', 'Manage Pricing', 'Manage credit pricing'),
    ('ADMIN_COMMISSION_MANAGE', 'Manage Commission', 'Manage commission rules'),
    ('ADMIN_CONFIG_MANAGE', 'Manage Configuration', 'Manage system configuration'),
    ('ADMIN_PERMANENT_BAN', 'Permanently Ban Users', 'Permanently ban users'),
    ('ADMIN_HIGH_VALUE_PAYOUT', 'Approve High Value Payouts', 'Approve high value payouts')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE (r.code = 'BUYER' AND p.code IN (
        'PROFILE_READ_SELF', 'PROFILE_UPDATE_SELF', 'PASSWORD_CHANGE_SELF',
        'SELLER_VERIFICATION_SUBMIT', 'SELLER_VERIFICATION_READ_SELF'))
   OR (r.code = 'SELLER' AND p.code IN (
        'PROFILE_READ_SELF', 'PROFILE_UPDATE_SELF', 'PASSWORD_CHANGE_SELF',
        'SELLER_VERIFICATION_READ_SELF', 'LISTING_CREATE_SELF', 'LISTING_PUBLISH_SELF'))
   OR (r.code = 'INSPECTOR' AND p.code IN (
        'PROFILE_READ_SELF', 'PROFILE_UPDATE_SELF', 'PASSWORD_CHANGE_SELF',
        'INSPECTION_REPORT_SUBMIT'))
   OR (r.code = 'INSPECTION_CENTER' AND p.code IN (
        'PROFILE_READ_SELF', 'PROFILE_UPDATE_SELF', 'PASSWORD_CHANGE_SELF'))
   OR (r.code = 'STAFF' AND p.code IN (
        'PROFILE_READ_SELF', 'PROFILE_UPDATE_SELF', 'PASSWORD_CHANGE_SELF',
        'USER_READ_ANY', 'SELLER_VERIFICATION_READ_ANY', 'SELLER_VERIFICATION_REVIEW',
        'ROLE_READ', 'STAFF_LISTING_REVIEW', 'STAFF_FRAUD_REVIEW',
        'STAFF_DISPUTE_REVIEW', 'STAFF_PAYOUT_REVIEW', 'STAFF_PAYOUT_HOLD',
        'STAFF_USER_WARN', 'STAFF_USER_TEMP_RESTRICT'))
   OR r.code = 'ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;
