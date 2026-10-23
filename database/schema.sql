-- V2V Database Schema 
CREATE DATABASE IF NOT EXISTS v2v_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE v2v_db;

-- All copied/aligned to ERD
-- Changed a lot of string/varchar types to enum
-- Authors: Avela Tom & Oladele Ibitomi


-- USER MANAGEMENT

CREATE TABLE USERS (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    student_number VARCHAR(20) NOT NULL UNIQUE,
    full_name VARCHAR(50) NOT NULL,
    email VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    phone_number VARCHAR(15),
    campus_location ENUM('Tygervalley', 'Mowbray') NOT NULL,
    aggregate_rating DECIMAL(3,2),
    user_role ENUM('student', 'administrator') NOT NULL DEFAULT 'student',
    account_status ENUM('pending_verification', 'active', 'suspended', 'deactivated') NOT NULL DEFAULT 'pending_verification', -- Deactivated includes deleted accounts, records kept for history
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Additional atributes for profile management
    bio TEXT NULL,
    profile_photo_url VARCHAR(255) NULL,
    connections INT NULL,
    profile_visibility ENUM('public', 'connections_only', 'private') NOT NULL DEFAULT 'public'
);

CREATE TABLE ACCOUNT_VERIFICATIONS (
    verification_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    verification_type ENUM('email') NOT NULL, -- Needs moderation
    verification_status ENUM('verified', 'not verified') NOT NULL DEFAULT 'not verified',
    verification_code VARCHAR(10) NULL, -- "token" seemed ambigious so renamed to "code"
    expires_at DATETIME NULL,
    verified_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES USERS(user_id) ON DELETE CASCADE -- ON DELETE CASCADE ensures table rows will be deleted if a User deletes their account
);


-- ACADEMIC MODULES & SKILLS


CREATE TABLE MODULES (
    module_id INT AUTO_INCREMENT PRIMARY KEY,
    module_code VARCHAR(20) NOT NULL UNIQUE,
    module_name VARCHAR(150) NOT NULL,
    faculty VARCHAR(50) NOT NULL
);

CREATE TABLE USER_MODULE_SKILLS (
    skill_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    module_id INT NOT NULL,
    skill_type ENUM('studying', 'offering', 'requiring') NOT NULL,
    proficiency_level ENUM('beginner', 'intermediate', 'advanced') NULL,
    FOREIGN KEY (user_id) REFERENCES USERS(user_id) ON DELETE CASCADE,
    FOREIGN KEY (module_id) REFERENCES MODULES(module_id) ON DELETE CASCADE,
    UNIQUE KEY uq_user_module_type (user_id, module_id, skill_type) -- a module can only be in each list once
);


-- PEER MATCHING


CREATE TABLE MATCH_REQUESTS (
    match_id INT AUTO_INCREMENT PRIMARY KEY,
    learner_id INT NOT NULL,
    helper_id INT NULL, -- filled once a match is confirmed
    required_module_id INT NOT NULL, -- module the learner needs help with
    offered_module_id INT NOT NULL, -- module the learner offers in return for SKILL_SWAP
    swapped_module_id INT NULL,     --  doesn't apply to ONE-WAY-HELP
    match_type ENUM('SKILL_SWAP', 'ONE_WAY_HELP') NOT NULL,
    match_status ENUM('pending', 'matched', 'declined', 'completed') NOT NULL DEFAULT 'pending',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (learner_id) REFERENCES USERS(user_id) ON DELETE CASCADE,
    FOREIGN KEY (helper_id) REFERENCES USERS(user_id) ON DELETE SET NULL, -- Helper id will be set to null if helper deletes their account
    FOREIGN KEY (offered_module_id) REFERENCES MODULES(module_id) ON DELETE RESTRICT, -- Disallows complete module deletion due to dependency
    FOREIGN KEY (required_module_id) REFERENCES MODULES(module_id) ON DELETE RESTRICT,
    FOREIGN KEY (swapped_module_id) REFERENCES MODULES(module_id) ON DELETE RESTRICT
);

CREATE TABLE SUPPORT_SESSIONS (
    session_id INT AUTO_INCREMENT PRIMARY KEY,
    match_id INT NOT NULL,
    scheduled_start DATETIME NOT NULL,
    scheduled_END DATETIME NOT NULL,
    location_type ENUM('Online', 'Tygervalley', 'Mowbray') NULL, -- Double check with Charmaine that this is correct
    session_status ENUM('scheduled', 'completed', 'cancelled') NOT NULL DEFAULT 'scheduled',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (match_id) REFERENCES MATCH_REQUESTS(match_id) ON DELETE CASCADE
);

CREATE TABLE REVIEWS (
    review_id INT AUTO_INCREMENT PRIMARY KEY,
    session_id INT NULL, -- Made optional for FR16, feedback can be given without a support session
    reviewer_id INT NOT NULL,
    reviewee_id INT NOT NULL,
    rating_score INT NOT NULL,
    feedback_text TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES SUPPORT_SESSIONS(session_id) ON DELETE CASCADE,
    FOREIGN KEY (reviewer_id) REFERENCES USERS(user_id) ON DELETE CASCADE,
    FOREIGN KEY (reviewee_id) REFERENCES USERS(user_id) ON DELETE CASCADE,
    CONSTRAINT check_rating_range CHECK (rating_score BETWEEN 1 AND 5)
);


-- CONNECTIONS


CREATE TABLE CONNECTION_REQUESTS (
    connection_id INT AUTO_INCREMENT PRIMARY KEY,
    connection_sender_id INT NOT NULL,
    connection_receiver_id INT NOT NULL,
    connection_status ENUM('pending', 'accepted', 'declined') NOT NULL DEFAULT 'pending',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at DATETIME NULL,
    FOREIGN KEY (connection_sender_id) REFERENCES USERS(user_id) ON DELETE CASCADE,
    FOREIGN KEY (connection_receiver_id) REFERENCES USERS(user_id) ON DELETE CASCADE
);


-- STUDY GROUPS


CREATE TABLE STUDY_GROUPS (
    group_id INT AUTO_INCREMENT PRIMARY KEY,
    group_name VARCHAR(50) NOT NULL,
    description VARCHAR(255) NULL, -- Added for FR11 (group topic/description)
    module_id INT NOT NULL,
    creator_id INT NOT NULL,
    max_members INT NOT NULL DEFAULT 5,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (module_id) REFERENCES MODULES(module_id) ON DELETE RESTRICT,
    FOREIGN KEY (creator_id) REFERENCES USERS(user_id) ON DELETE RESTRICT
);



CREATE TABLE GROUP_MEMBERS (
    group_id INT NOT NULL,
    user_id INT NOT NULL,
    PRIMARY KEY (group_id, user_id),
    member_role ENUM('member', 'creator') NOT NULL DEFAULT 'member', -- Added attribute
    joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (group_id) REFERENCES STUDY_GROUPS(group_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES USERS(user_id) ON DELETE CASCADE
);


-- COMMUNICATION


CREATE TABLE CONVERSATIONS (
    conversation_id INT AUTO_INCREMENT PRIMARY KEY,
    conversation_type ENUM('direct', 'group') NOT NULL,
    group_id INT NULL, -- Only filled if type is Group
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (group_id) REFERENCES STUDY_GROUPS(group_id) ON DELETE CASCADE
);

CREATE TABLE CONVERSATION_MEMBERS (
    conversation_id INT NOT NULL,
    user_id INT NOT NULL,
    PRIMARY KEY (conversation_id, user_id),
    joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (conversation_id) REFERENCES CONVERSATIONS(conversation_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES USERS(user_id) ON DELETE CASCADE
);

CREATE TABLE MESSAGES (
    message_id INT AUTO_INCREMENT PRIMARY KEY,
    conversation_id INT NOT NULL,
    sender_id INT NOT NULL,
    message_text TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (conversation_id) REFERENCES CONVERSATIONS(conversation_id) ON DELETE CASCADE,
    FOREIGN KEY (sender_id) REFERENCES USERS(user_id) ON DELETE CASCADE
);

CREATE TABLE MESSAGE_ATTACHMENTS (
    attachment_id INT AUTO_INCREMENT PRIMARY KEY,
    message_id INT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_url VARCHAR(255) NOT NULL,
    file_type VARCHAR(10) NULL, -- should probably implement restrictions here
    file_size INT NOT NULL, -- should probably implement restrictions here
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (message_id) REFERENCES MESSAGES(message_id) ON DELETE CASCADE
);


CREATE INDEX idx_messages_conversation ON MESSAGES (conversation_id, message_id); -- speeds up loading chats


-- NOTIFICATIONS


CREATE TABLE NOTIFICATIONS (
    notification_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    notification_title ENUM('request' , 'group activity', 'message', 'session reminder', 'moderation') NOT NULL, -- Struggled here, needs moderation 
    notification_message TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES USERS(user_id) ON DELETE CASCADE
);

CREATE TABLE DEVICE_TOKENS (
    device_token_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    device_token VARCHAR(255) NOT NULL UNIQUE, -- Specified as unique key, Needs moderation
    device_type ENUM('android', 'ios') NOT NULL DEFAULT 'android', -- Needs moderation/clarification, all devices will obviously already be android. ios included simply for "future proof"
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, -- Needs moderation 
    FOREIGN KEY (user_id) REFERENCES USERS(user_id) ON DELETE CASCADE
);


-- SESSION MANAGEMENT

-- Tokens of users who logged out (FR18). Old ones get cleaned up on logout
CREATE TABLE REVOKED_TOKENS (
    jti VARCHAR(64) PRIMARY KEY,
    expires_at DATETIME NOT NULL
);


-- MODERATION & AUDITING


CREATE TABLE REPORTS (
    report_id INT AUTO_INCREMENT PRIMARY KEY,
    reporter_id INT NOT NULL,
    reported_user_id INT NULL,
    report_reason ENUM('harassment', 'spam', 'academic misconduct', 'inappropriate content', 'other') NOT NULL DEFAULT 'other', -- Reason / Subject. Could be VARCHAR?
    report_description TEXT NOT NULL, -- Added field. Separated reason to reason and description
    report_status ENUM('submitted', 'under review', 'resolved', 'dismissed') NOT NULL DEFAULT 'submitted',
    action_taken VARCHAR(50) NULL, 
    admin_notes VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (reporter_id) REFERENCES USERS(user_id) ON DELETE CASCADE,
    FOREIGN KEY (reported_user_id) REFERENCES USERS(user_id) ON DELETE SET NULL
);

CREATE TABLE MODERATION_ACTIONS (
    moderation_action_id INT AUTO_INCREMENT PRIMARY KEY,
    report_id INT NULL,
    moderator_id INT NOT NULL,
    target_user_id INT NULL,
    action_type ENUM('warning', 'suspend', 'reinstate', 'deactivate', 'delete_content', 'dismiss_report') NOT NULL, -- Needs moderation
    action_reason TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (report_id) REFERENCES REPORTS(report_id) ON DELETE SET NULL,
    FOREIGN KEY (moderator_id) REFERENCES USERS(user_id) ON DELETE RESTRICT,
    FOREIGN KEY (target_user_id) REFERENCES USERS(user_id) ON DELETE SET NULL
);

CREATE TABLE AUDIT_EVENTS (
    audit_event_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NULL,
    event_type VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50) NOT NULL, -- Needs clarification
    entity_id INT NULL,
    event_description TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES USERS(user_id) ON DELETE SET NULL
);



