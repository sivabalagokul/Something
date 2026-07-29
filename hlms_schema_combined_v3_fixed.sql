-- ============================================================
-- HLMS - Housing Loan Management System
-- PostgreSQL Database Schema (COMBINED v2 + v3)
--
-- This is hlms_schema_updated_v2.sql and schema_additions_v3.sql
-- merged into a single runnable file, in the order they need to
-- run in (v3's two new tables are placed after the v2 views,
-- before any seed data is inserted). No table, column, or field
-- names were changed - this is a straight concatenation.
--
-- WHAT'S NEW IN v2 (flagged inline as "-- CHG(v2):"):
--  1. Every "record" table gets a deleted_at TIMESTAMPTZ column.
--     A row is considered active when deleted_at IS NULL, and
--     "deleted" when deleted_at holds the timestamp it was removed.
--     No data is ever physically removed by the app anymore.
--     audit_log is intentionally excluded - an audit trail should
--     never be deletable, soft or otherwise.
--  2. Two generic, reusable functions: soft_delete() and
--     restore_record(). They work for any table/PK combination,
--     so you don't need a bespoke function per table.
--  3. Cascading soft-delete triggers on loan_application and
--     auction_case, so deleting a loan application automatically
--     soft-deletes its documents, legal/bank decisions,
--     disbursement, insurance, EMIs, service requests, auction
--     case, notices etc. - and deleting an auction case cascades
--     to its notes and bids.
--  4. "v_active_*" views for the tables you'll query day-to-day,
--     so normal SELECTs don't have to remember to add
--     "WHERE deleted_at IS NULL" every time.
--
-- Everything from the previous version (surrogate PKs, merged
-- loan_draft/loan_application, bank_office_decision.status,
-- legal_verification info-request columns, BYTEA document
-- storage) is unchanged and included below for a single,
-- runnable file.
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================
-- 1. USERS & ACCESS CONTROL
-- ============================================================

CREATE TABLE app_user (
    email               VARCHAR(255) PRIMARY KEY,
    name                VARCHAR(150) NOT NULL,
    mobile              VARCHAR(20) NOT NULL,
    password_hash       VARCHAR(255) NOT NULL,
    role                VARCHAR(30) NOT NULL CHECK (role IN (
                            'customer', 'legal', 'bankoffice',
                            'bidder', 'admin'
                        )),
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    id_type             VARCHAR(30),
    id_number           VARCHAR(50),
    pref_sms            BOOLEAN NOT NULL DEFAULT TRUE,
    pref_email          BOOLEAN NOT NULL DEFAULT TRUE,
    pref_inapp          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- CHG(v2): soft delete marker. NULL = active, timestamp = deleted.
    deleted_at          TIMESTAMPTZ
);
CREATE INDEX idx_app_user_deleted ON app_user(deleted_at);

CREATE TABLE role_permission (
    permission_id  BIGSERIAL PRIMARY KEY,
    role           VARCHAR(30) NOT NULL,
    module         VARCHAR(50) NOT NULL,
    can_view       BOOLEAN NOT NULL DEFAULT FALSE,
    can_add        BOOLEAN NOT NULL DEFAULT FALSE,
    can_edit       BOOLEAN NOT NULL DEFAULT FALSE,
    can_delete     BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (role, module)
    -- No deleted_at here: this is a permission matrix, not a
    -- lifecycle record - rows are simply present or absent.
);

CREATE TABLE customer_employment_profile (
    profile_id      BIGSERIAL PRIMARY KEY,
    user_email      VARCHAR(255) NOT NULL UNIQUE REFERENCES app_user(email) ON DELETE CASCADE,
    employment_type VARCHAR(30),
    employer        VARCHAR(150),
    monthly_income  NUMERIC(14,2),
    other_emis      NUMERIC(14,2) DEFAULT 0,
    desired_amount  NUMERIC(14,2),
    pan             VARCHAR(20),
    deleted_at      TIMESTAMPTZ
);

-- ============================================================
-- 2. PRODUCTS & APPLICATIONS
-- ============================================================

CREATE TABLE loan_product (
    product_id       VARCHAR(50) PRIMARY KEY,
    name             VARCHAR(150) NOT NULL,
    category         VARCHAR(50),
    interest_rate    NUMERIC(5,2) NOT NULL,
    max_tenure_years INT NOT NULL,
    max_ltv          VARCHAR(10),
    description      TEXT,
    -- CHG(v2): lets you discontinue a product without breaking
    -- existing applications that still reference it.
    deleted_at       TIMESTAMPTZ
);

CREATE TABLE loan_application (
    app_id           VARCHAR(50) PRIMARY KEY,
    tracking_no      VARCHAR(50) UNIQUE,
    user_email       VARCHAR(255) NOT NULL REFERENCES app_user(email),
    product_id       VARCHAR(50) REFERENCES loan_product(product_id),
    loan_amount      NUMERIC(14,2),
    tenure_years     INT,
    purpose          VARCHAR(150),
    interest_rate    NUMERIC(5,2),
    status           VARCHAR(30) NOT NULL DEFAULT 'Draft' CHECK (status IN (
                        'Draft', 'Submitted', 'Under Review',
                        'Rejected', 'Disbursed'
                     )),
    stage_index      INT NOT NULL DEFAULT 0,

    current_step     INT NOT NULL DEFAULT 1,
    form_data        JSONB NOT NULL DEFAULT '{}'::jsonb,
    saved_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    applicant_name    VARCHAR(150),
    applicant_mobile  VARCHAR(20),
    applicant_pan     VARCHAR(20),

    addr_door_no      VARCHAR(30),
    addr_street       VARCHAR(150),
    addr_city         VARCHAR(100),
    addr_state        VARCHAR(100),
    addr_pincode      VARCHAR(10),

    employment_type   VARCHAR(30),
    employer          VARCHAR(150),
    monthly_income    NUMERIC(14,2),
    other_emis        NUMERIC(14,2),

    property_address  TEXT,
    property_type     VARCHAR(50),
    property_value    NUMERIC(14,2),

    -- CHG(v2): soft delete marker. Cascades to child records via trigger below.
    deleted_at        TIMESTAMPTZ
);
CREATE INDEX idx_loan_app_user ON loan_application(user_email);
CREATE INDEX idx_loan_app_product ON loan_application(product_id);
CREATE INDEX idx_loan_app_status ON loan_application(status);
CREATE INDEX idx_loan_app_deleted ON loan_application(deleted_at);

-- ============================================================
-- 3. DOCUMENTS & VERIFICATION
-- ============================================================

CREATE TABLE application_document (
    document_id          VARCHAR(50) PRIMARY KEY,
    app_id               VARCHAR(50) NOT NULL REFERENCES loan_application(app_id) ON DELETE CASCADE,
    doc_type             VARCHAR(50) NOT NULL,
    file_name            VARCHAR(255) NOT NULL,
    file_data            BYTEA NOT NULL,
    file_size            INT,
    uploaded_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    verification_status  VARCHAR(30) NOT NULL DEFAULT 'Pending',
    verified_by          VARCHAR(255) REFERENCES app_user(email),
    verified_at          TIMESTAMPTZ,
    remarks              TEXT,
    deleted_at           TIMESTAMPTZ
);
CREATE INDEX idx_app_doc_app ON application_document(app_id);
CREATE INDEX idx_app_doc_deleted ON application_document(deleted_at);

CREATE TABLE legal_verification (
    verification_id      BIGSERIAL PRIMARY KEY,
    app_id                VARCHAR(50) NOT NULL UNIQUE REFERENCES loan_application(app_id) ON DELETE CASCADE,
    decision              VARCHAR(30),
    decision_date         TIMESTAMPTZ,
    officer               VARCHAR(255) REFERENCES app_user(email),
    remarks               TEXT,
    info_requested        BOOLEAN NOT NULL DEFAULT FALSE,
    info_request_details  TEXT,
    info_requested_at     TIMESTAMPTZ,
    deleted_at            TIMESTAMPTZ
);

CREATE TABLE legal_checklist_item (
    checklist_item_id  VARCHAR(50) PRIMARY KEY,
    app_id             VARCHAR(50) NOT NULL REFERENCES loan_application(app_id) ON DELETE CASCADE,
    checklist_key      VARCHAR(100) NOT NULL,
    status             VARCHAR(30) NOT NULL DEFAULT 'Pending',
    deleted_at         TIMESTAMPTZ
);
CREATE INDEX idx_checklist_app ON legal_checklist_item(app_id);

CREATE TABLE bank_office_decision (
    decision_id    BIGSERIAL PRIMARY KEY,
    app_id         VARCHAR(50) NOT NULL UNIQUE REFERENCES loan_application(app_id) ON DELETE CASCADE,
    status         VARCHAR(30),
    officer        VARCHAR(255) REFERENCES app_user(email),
    decision_date  TIMESTAMPTZ,
    reason         VARCHAR(255),
    remarks        TEXT,
    deleted_at     TIMESTAMPTZ
);

-- ============================================================
-- 4. DISBURSEMENT, INSURANCE & REPAYMENT
-- ============================================================

CREATE TABLE disbursement (
    disbursement_id  BIGSERIAL PRIMARY KEY,
    app_id           VARCHAR(50) NOT NULL UNIQUE REFERENCES loan_application(app_id) ON DELETE CASCADE,
    disbursed_date   TIMESTAMPTZ,
    amount           NUMERIC(14,2),
    ref_no           VARCHAR(50),
    mode             VARCHAR(30),
    bank_details     VARCHAR(150),
    deleted_at       TIMESTAMPTZ
);

CREATE TABLE insurance_policy (
    policy_id    VARCHAR(50) PRIMARY KEY,
    app_id       VARCHAR(50) NOT NULL REFERENCES loan_application(app_id) ON DELETE CASCADE,
    policy_type  VARCHAR(50) CHECK (policy_type IN ('property', 'life')),
    status       VARCHAR(30) NOT NULL DEFAULT 'Active',
    valid_till   DATE,
    deleted_at   TIMESTAMPTZ
);
CREATE INDEX idx_insurance_app ON insurance_policy(app_id);

CREATE TABLE emi_installment (
    installment_id       VARCHAR(50) PRIMARY KEY,
    app_id               VARCHAR(50) NOT NULL REFERENCES loan_application(app_id) ON DELETE CASCADE,
    installment_no       INT NOT NULL,
    due_date             DATE NOT NULL,
    emi_amount           NUMERIC(14,2) NOT NULL,
    principal_component  NUMERIC(14,2),
    interest_component   NUMERIC(14,2),
    closing_balance      NUMERIC(14,2),
    status               VARCHAR(30) NOT NULL DEFAULT 'Due',
    paid_date            DATE,
    deleted_at           TIMESTAMPTZ,
    UNIQUE (app_id, installment_no)
);
CREATE INDEX idx_emi_app ON emi_installment(app_id);
CREATE INDEX idx_emi_status ON emi_installment(status);

-- ============================================================
-- 5. SERVICE REQUESTS & POST-DISBURSEMENT DOCS
-- ============================================================

CREATE TABLE service_request (
    request_id    VARCHAR(50) PRIMARY KEY,
    ref_no        VARCHAR(50) UNIQUE NOT NULL,
    app_id        VARCHAR(50) REFERENCES loan_application(app_id) ON DELETE CASCADE,
    user_email    VARCHAR(255) NOT NULL REFERENCES app_user(email),
    request_type  VARCHAR(50) NOT NULL,
    description   TEXT,
    status        VARCHAR(30) NOT NULL DEFAULT 'Requested',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ
);
CREATE INDEX idx_service_req_user ON service_request(user_email);
CREATE INDEX idx_service_req_app ON service_request(app_id);
CREATE INDEX idx_service_req_deleted ON service_request(deleted_at);

CREATE TABLE post_disbursement_document (
    pd_doc_id     VARCHAR(50) PRIMARY KEY,
    app_id        VARCHAR(50) NOT NULL REFERENCES loan_application(app_id) ON DELETE CASCADE,
    doc_type      VARCHAR(80) NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    file_data     BYTEA NOT NULL,
    file_size     INT,
    note          TEXT,
    status        VARCHAR(30) NOT NULL DEFAULT 'Pending',
    uploaded_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    verified_by   VARCHAR(255) REFERENCES app_user(email),
    verified_at   TIMESTAMPTZ,
    remarks       TEXT,
    deleted_at    TIMESTAMPTZ
);
CREATE INDEX idx_pd_doc_app ON post_disbursement_document(app_id);

-- ============================================================
-- 6. AUCTION & LEGAL NOTICES (SARFAESI)
-- ============================================================

CREATE TABLE auction_case (
    auction_id       BIGSERIAL PRIMARY KEY,
    app_id           VARCHAR(50) NOT NULL UNIQUE REFERENCES loan_application(app_id) ON DELETE CASCADE,
    stage            VARCHAR(30) NOT NULL DEFAULT 'None' CHECK (stage IN (
                        'None', 'Notice Issued', 'Possession',
                        'Auction Scheduled', 'Resolved'
                     )),
    notice_date      DATE,
    notice_due_date  DATE,
    demand_amount    NUMERIC(14,2),
    possession_date  DATE,
    auction_date     DATE,
    reserve_price    NUMERIC(14,2),
    deleted_at       TIMESTAMPTZ
);

CREATE TABLE auction_note (
    note_id    VARCHAR(50) PRIMARY KEY,
    app_id     VARCHAR(50) NOT NULL REFERENCES auction_case(app_id) ON DELETE CASCADE,
    note_date  TIMESTAMPTZ NOT NULL DEFAULT now(),
    note_text  TEXT NOT NULL,
    deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_auction_note_app ON auction_note(app_id);

CREATE TABLE legal_notice (
    notice_id    VARCHAR(50) PRIMARY KEY,
    notice_no    VARCHAR(50) UNIQUE NOT NULL,
    app_id       VARCHAR(50) NOT NULL REFERENCES loan_application(app_id) ON DELETE CASCADE,
    notice_type  VARCHAR(50) NOT NULL,
    issue_date   DATE NOT NULL,
    due_date     DATE,
    status       VARCHAR(30) NOT NULL DEFAULT 'Active',
    officer      VARCHAR(255) REFERENCES app_user(email),
    content      TEXT,
    deleted_at   TIMESTAMPTZ
);
CREATE INDEX idx_legal_notice_app ON legal_notice(app_id);

CREATE TABLE bid (
    bid_id        VARCHAR(50) PRIMARY KEY,
    app_id        VARCHAR(50) NOT NULL REFERENCES auction_case(app_id) ON DELETE CASCADE,
    bidder_email  VARCHAR(255) NOT NULL REFERENCES app_user(email),
    bid_amount    NUMERIC(14,2) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ
);
CREATE INDEX idx_bid_app ON bid(app_id);
CREATE INDEX idx_bid_bidder ON bid(bidder_email);

-- ============================================================
-- 7. NOTIFICATIONS & AUDIT
-- ============================================================

CREATE TABLE notification (
    notification_id  VARCHAR(50) PRIMARY KEY,
    user_email       VARCHAR(255) NOT NULL REFERENCES app_user(email) ON DELETE CASCADE,
    title            VARCHAR(150) NOT NULL,
    body             TEXT,
    channels         TEXT[] NOT NULL DEFAULT ARRAY['inapp'],
    is_read          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ
);
CREATE INDEX idx_notification_user ON notification(user_email);
CREATE INDEX idx_notification_read ON notification(user_email, is_read);

-- audit_log intentionally has NO deleted_at / soft delete: an audit
-- trail must remain a permanent, untouchable record of what happened.
CREATE TABLE audit_log (
    log_id       BIGSERIAL PRIMARY KEY,
    entity_type  VARCHAR(50) NOT NULL,
    entity_id    VARCHAR(50) NOT NULL,
    action       VARCHAR(30) NOT NULL,
    actor_email  VARCHAR(255) REFERENCES app_user(email),
    before_data  JSONB,
    after_data   JSONB,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_entity ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_actor ON audit_log(actor_email);

-- ============================================================
-- 8. SOFT DELETE FUNCTIONS  (CHG(v2))
-- ============================================================

-- Generic soft delete: works on any table/primary-key-column pair.
-- Returns TRUE if a row was found and marked deleted, FALSE if the
-- row didn't exist or was already deleted.
-- Example:  SELECT soft_delete('loan_application', 'app_id', 'APP-1002');
CREATE OR REPLACE FUNCTION soft_delete(
    p_table      TEXT,
    p_pk_column  TEXT,
    p_pk_value   TEXT
) RETURNS BOOLEAN
LANGUAGE plpgsql
AS $fn_soft_delete$
DECLARE
    affected INT;
BEGIN
    EXECUTE format(
        'UPDATE %I SET deleted_at = now() WHERE %I::text = $1 AND deleted_at IS NULL',
        p_table, p_pk_column
    ) USING p_pk_value;
    GET DIAGNOSTICS affected = ROW_COUNT;
    RETURN affected > 0;
END;
$fn_soft_delete$;

-- Generic restore: undoes a soft delete.
-- Example:  SELECT restore_record('loan_application', 'app_id', 'APP-1002');
CREATE OR REPLACE FUNCTION restore_record(
    p_table      TEXT,
    p_pk_column  TEXT,
    p_pk_value   TEXT
) RETURNS BOOLEAN
LANGUAGE plpgsql
AS $fn_restore_record$
DECLARE
    affected INT;
BEGIN
    EXECUTE format(
        'UPDATE %I SET deleted_at = NULL WHERE %I::text = $1 AND deleted_at IS NOT NULL',
        p_table, p_pk_column
    ) USING p_pk_value;
    GET DIAGNOSTICS affected = ROW_COUNT;
    RETURN affected > 0;
END;
$fn_restore_record$;

-- Cascade: soft-deleting a loan_application also soft-deletes every
-- dependent record for that app_id, so a deleted application doesn't
-- leave "orphaned" active documents, decisions, EMIs, etc. behind.
CREATE OR REPLACE FUNCTION cascade_soft_delete_loan_application()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $fn_cascade_loan$
BEGIN
    IF NEW.deleted_at IS NOT NULL AND OLD.deleted_at IS NULL THEN
        UPDATE application_document        SET deleted_at = NEW.deleted_at WHERE app_id = NEW.app_id AND deleted_at IS NULL;
        UPDATE legal_verification          SET deleted_at = NEW.deleted_at WHERE app_id = NEW.app_id AND deleted_at IS NULL;
        UPDATE legal_checklist_item        SET deleted_at = NEW.deleted_at WHERE app_id = NEW.app_id AND deleted_at IS NULL;
        UPDATE bank_office_decision        SET deleted_at = NEW.deleted_at WHERE app_id = NEW.app_id AND deleted_at IS NULL;
        UPDATE disbursement                SET deleted_at = NEW.deleted_at WHERE app_id = NEW.app_id AND deleted_at IS NULL;
        UPDATE insurance_policy            SET deleted_at = NEW.deleted_at WHERE app_id = NEW.app_id AND deleted_at IS NULL;
        UPDATE emi_installment             SET deleted_at = NEW.deleted_at WHERE app_id = NEW.app_id AND deleted_at IS NULL;
        UPDATE service_request             SET deleted_at = NEW.deleted_at WHERE app_id = NEW.app_id AND deleted_at IS NULL;
        UPDATE post_disbursement_document  SET deleted_at = NEW.deleted_at WHERE app_id = NEW.app_id AND deleted_at IS NULL;
        UPDATE auction_case                SET deleted_at = NEW.deleted_at WHERE app_id = NEW.app_id AND deleted_at IS NULL;
        UPDATE legal_notice                SET deleted_at = NEW.deleted_at WHERE app_id = NEW.app_id AND deleted_at IS NULL;
    END IF;
    RETURN NEW;
END;
$fn_cascade_loan$;

CREATE TRIGGER trg_cascade_soft_delete_loan_application
AFTER UPDATE OF deleted_at ON loan_application
FOR EACH ROW
EXECUTE FUNCTION cascade_soft_delete_loan_application();

-- Cascade: soft-deleting an auction_case also soft-deletes its notes and bids.
CREATE OR REPLACE FUNCTION cascade_soft_delete_auction_case()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $fn_cascade_auction$
BEGIN
    IF NEW.deleted_at IS NOT NULL AND OLD.deleted_at IS NULL THEN
        UPDATE auction_note SET deleted_at = NEW.deleted_at WHERE app_id = NEW.app_id AND deleted_at IS NULL;
        UPDATE bid          SET deleted_at = NEW.deleted_at WHERE app_id = NEW.app_id AND deleted_at IS NULL;
    END IF;
    RETURN NEW;
END;
$fn_cascade_auction$;

CREATE TRIGGER trg_cascade_soft_delete_auction_case
AFTER UPDATE OF deleted_at ON auction_case
FOR EACH ROW
EXECUTE FUNCTION cascade_soft_delete_auction_case();

-- ============================================================
-- 9. CONVENIENCE VIEWS FOR ACTIVE (NON-DELETED) RECORDS (CHG(v2))
-- Query these instead of the base tables in day-to-day app code so
-- you never have to remember "WHERE deleted_at IS NULL" by hand.
-- ============================================================

CREATE VIEW v_active_app_user AS
    SELECT * FROM app_user WHERE deleted_at IS NULL;

CREATE VIEW v_active_loan_product AS
    SELECT * FROM loan_product WHERE deleted_at IS NULL;

CREATE VIEW v_active_loan_application AS
    SELECT * FROM loan_application WHERE deleted_at IS NULL;

CREATE VIEW v_active_application_document AS
    SELECT * FROM application_document WHERE deleted_at IS NULL;

CREATE VIEW v_active_service_request AS
    SELECT * FROM service_request WHERE deleted_at IS NULL;

CREATE VIEW v_active_notification AS
    SELECT * FROM notification WHERE deleted_at IS NULL;

-- ============================================================
-- 10. OTP VERIFICATION & ELIGIBILITY ASSESSMENT (schema_additions_v3.sql, merged in)
-- Same soft-delete convention as the rest of the schema; field
-- names kept exactly as originally written.
-- ============================================================

CREATE TABLE otp_verification (
    otp_id       VARCHAR(50) PRIMARY KEY,
    email        VARCHAR(255) NOT NULL REFERENCES app_user(email) ON DELETE CASCADE,
    otp_code     VARCHAR(10) NOT NULL,
    purpose      VARCHAR(30) NOT NULL DEFAULT 'LOGIN',
    expires_at   TIMESTAMPTZ NOT NULL,
    consumed     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ
);
CREATE INDEX idx_otp_email ON otp_verification(email);
CREATE INDEX idx_otp_email_purpose ON otp_verification(email, purpose);

CREATE TABLE eligibility_assessment (
    assessment_id           BIGSERIAL PRIMARY KEY,
    user_email              VARCHAR(255) NOT NULL REFERENCES app_user(email) ON DELETE CASCADE,
    requested_amount        NUMERIC(14,2),
    tenure_years            INT,
    cibil_score             INT,
    monthly_income          NUMERIC(14,2),
    other_emis              NUMERIC(14,2),
    interest_rate           NUMERIC(5,2),
    status                  VARCHAR(30) NOT NULL,
    max_eligible_loan_amount NUMERIC(14,2),
    estimated_emi           NUMERIC(14,2),
    dti_ratio               NUMERIC(6,4),
    reasons                 JSONB,
    recommendations         JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at              TIMESTAMPTZ
);
CREATE INDEX idx_eligibility_user ON eligibility_assessment(user_email);
CREATE INDEX idx_eligibility_created ON eligibility_assessment(created_at);

-- SEED DATA
-- ============================================================

-- ---- Loan products ----
INSERT INTO loan_product (product_id, name, category, interest_rate, max_tenure_years, max_ltv, description) VALUES
('HP',  'Home Purchase Loan',          'Home Purchase',    8.50, 30, '90%', 'Finance the purchase of a new or resale residential property.'),
('HC',  'Home Construction Loan',      'Construction',     8.75, 25, '85%', 'Funds disbursed in stages to construct your house on owned land.'),
('HR',  'Home Renovation Loan',        'Renovation',       9.25, 15, '80%', 'Renovate, repair or extend your existing home.'),
('PL',  'Plot Loan',                   'Plot Loan',        9.00, 20, '75%', 'Purchase a residential plot for future construction.'),
('BT',  'Balance Transfer + Top-up',   'Balance Transfer', 8.40, 30, '90%', 'Transfer your existing home loan and get an additional top-up amount.'),
('NRI', 'NRI Home Loan',               'Home Purchase',    8.90, 20, '80%', 'Home loan tailored for Non-Resident Indian customers.');

-- ---- Users (one per role) ----
INSERT INTO app_user (email, name, mobile, password_hash, role, id_type, id_number) VALUES
('arun.customer@example.com',  'Arun Kumar',       '9840011122', crypt('Password@123', gen_salt('bf')), 'customer',   'Aadhaar', '1234-5678-9012'),
('priya.customer@example.com', 'Priya Raman',      '9840022233', crypt('Password@123', gen_salt('bf')), 'customer',   'PAN',     'ABCPP1234D'),
('legal.officer@example.com',  'Kavitha Legal',    '9840033344', crypt('Password@123', gen_salt('bf')), 'legal',      NULL,      NULL),
('bank.officer@example.com',   'Suresh BankOff',   '9840044455', crypt('Password@123', gen_salt('bf')), 'bankoffice', NULL,      NULL),
('vijay.bidder@example.com',   'Vijay Bidder',     '9840055566', crypt('Password@123', gen_salt('bf')), 'bidder',     'PAN',     'BIDPV5678K'),
('admin@example.com',          'System Admin',     '9840066677', crypt('Admin@123', gen_salt('bf')),    'admin',      NULL,      NULL);

-- ---- Role permissions ----
INSERT INTO role_permission (role, module, can_view, can_add, can_edit, can_delete) VALUES
('customer',   'loan_application', TRUE,  TRUE,  TRUE,  FALSE),
('legal',      'loan_application', TRUE,  FALSE, TRUE,  FALSE),
('bankoffice', 'loan_application', TRUE,  FALSE, TRUE,  FALSE),
('bidder',     'auction_case',     TRUE,  FALSE, FALSE, FALSE),
('admin',      'loan_application', TRUE,  TRUE,  TRUE,  TRUE);

-- ---- Employment profiles ----
INSERT INTO customer_employment_profile (user_email, employment_type, employer, monthly_income, other_emis, desired_amount, pan) VALUES
('arun.customer@example.com',  'Salaried',       'TCS Ltd',        95000.00, 8000.00, 4500000.00, 'ARUNP1234E'),
('priya.customer@example.com', 'Self-Employed',  'Raman Textiles', 120000.00, 0.00,   6000000.00, 'PRIYP5678F');

-- ---- Loan applications ----
INSERT INTO loan_application (
    app_id, tracking_no, user_email, product_id, loan_amount, tenure_years, purpose,
    interest_rate, status, stage_index, current_step, form_data,
    applicant_name, applicant_mobile, applicant_pan,
    addr_door_no, addr_street, addr_city, addr_state, addr_pincode,
    employment_type, employer, monthly_income, other_emis,
    property_address, property_type, property_value
) VALUES
('APP-1001', 'TRK-100001', 'arun.customer@example.com', 'HP', 4500000.00, 20, 'Purchase of resale flat',
 8.50, 'Under Review', 2, 6, '{}'::jsonb,
 'Arun Kumar', '9840011122', 'ARUNP1234E',
 '12', 'Anna Nagar Main Road', 'Chennai', 'Tamil Nadu', '600040',
 'Salaried', 'TCS Ltd', 95000.00, 8000.00,
 'Flat 4B, Anna Nagar, Chennai', 'Apartment', 5200000.00),
('APP-1002', NULL, 'priya.customer@example.com', 'HC', 6000000.00, 15, NULL,
 NULL, 'Draft', 0, 3,
 '{"personal": {"name": "Priya Raman"}, "property": {"type": "Independent House"}}'::jsonb,
 'Priya Raman', '9840022233', 'PRIYP5678F',
 NULL, NULL, 'Coimbatore', 'Tamil Nadu', NULL,
 'Self-Employed', 'Raman Textiles', 120000.00, 0.00,
 NULL, NULL, NULL),
-- A third application, used below purely to demonstrate soft delete.
('APP-1003', 'TRK-100003', 'priya.customer@example.com', 'PL', 1500000.00, 10, 'Plot purchase in Coimbatore',
 9.00, 'Submitted', 1, 6, '{}'::jsonb,
 'Priya Raman', '9840022233', 'PRIYP5678F',
 '45', 'RS Puram', 'Coimbatore', 'Tamil Nadu', '641002',
 'Self-Employed', 'Raman Textiles', 120000.00, 0.00,
 NULL, 'Plot', 1500000.00);

-- ---- Application documents (BYTEA file content) ----
INSERT INTO application_document (document_id, app_id, doc_type, file_name, file_data, file_size, verification_status) VALUES
('DOC-1', 'APP-1001', 'idProof',      'aadhaar_arun.pdf',   convert_to('Sample idProof file content', 'UTF8'),      28, 'Verified'),
('DOC-2', 'APP-1001', 'incomeProof',  'salary_slip.pdf',    convert_to('Sample incomeProof file content', 'UTF8'),  32, 'Verified'),
('DOC-3', 'APP-1001', 'propertyDocs', 'sale_deed.pdf',      convert_to('Sample propertyDocs file content', 'UTF8'), 34, 'Pending');

-- ---- Legal verification: shows the legal team requesting more info ----
INSERT INTO legal_verification (app_id, decision, decision_date, officer, remarks, info_requested, info_request_details, info_requested_at) VALUES
('APP-1001', 'Query Raised', now(), 'legal.officer@example.com',
 'Encumbrance certificate is incomplete; sale deed needs re-verification.',
 TRUE, 'Please upload a fresh Encumbrance Certificate covering the last 15 years, and a notarized copy of the sale deed.', now());

-- ---- Legal checklist ----
INSERT INTO legal_checklist_item (checklist_item_id, app_id, checklist_key, status) VALUES
('CHK-1', 'APP-1001', 'titleDeed',    'Verified'),
('CHK-2', 'APP-1001', 'encumbrance',  'Pending'),
('CHK-3', 'APP-1001', 'saleDeed',     'Query Raised'),
('CHK-4', 'APP-1001', 'noc',          'Pending'),
('CHK-5', 'APP-1001', 'valuation',    'Pending'),
('CHK-6', 'APP-1001', 'taxReceipt',   'Pending');

-- ---- Bank office decision (status column, renamed from decision) ----
INSERT INTO bank_office_decision (app_id, status, officer, decision_date, reason, remarks) VALUES
('APP-1001', 'Pending', 'bank.officer@example.com', NULL, NULL, 'Awaiting legal clearance before final decision.');

-- ---- Service request: customer responding to the additional document request ----
INSERT INTO service_request (request_id, ref_no, app_id, user_email, request_type, description, status) VALUES
('SR-1', 'SR-REF-001', 'APP-1001', 'arun.customer@example.com', 'Additional Document Submission',
 'Uploading fresh Encumbrance Certificate and notarized sale deed as requested by legal team.', 'Requested');

-- ---- Post-disbursement document (BYTEA) - the extra document the customer supplies ----
INSERT INTO post_disbursement_document (pd_doc_id, app_id, doc_type, file_name, file_data, file_size, note, status) VALUES
('PDD-1', 'APP-1001', 'Other Supporting Document', 'encumbrance_certificate_fresh.pdf',
 convert_to('Sample encumbrance certificate content', 'UTF8'), 40,
 'Re-uploaded as requested by legal team.', 'Pending');

-- ---- Notification to the customer about the info request ----
INSERT INTO notification (notification_id, user_email, title, body, channels) VALUES
('NOTIF-1', 'arun.customer@example.com', 'Additional documents required',
 'Our legal team needs a fresh Encumbrance Certificate and a notarized sale deed for your application APP-1001.',
 ARRAY['inapp','sms','email']);

-- ---- Audit log entry ----
INSERT INTO audit_log (entity_type, entity_id, action, actor_email, before_data, after_data) VALUES
('legal_verification', 'APP-1001', 'UPDATE', 'legal.officer@example.com',
 '{"info_requested": false}'::jsonb, '{"info_requested": true}'::jsonb);

-- ============================================================
-- SOFT DELETE - WORKED EXAMPLE
-- APP-1003 was created purely to demonstrate this: the customer
-- withdraws the plot loan application, and it's soft-deleted along
-- with anything attached to it (there's nothing else attached yet,
-- but the trigger would handle it the same way either way).
-- ============================================================

-- Soft-delete the application (cascades to any child rows automatically):
SELECT soft_delete('loan_application', 'app_id', 'APP-1003');

-- To list only active applications:
--   SELECT * FROM v_active_loan_application;
-- or directly:
--   SELECT * FROM loan_application WHERE deleted_at IS NULL;

-- To undo the delete:
--   SELECT restore_record('loan_application', 'app_id', 'APP-1003');
