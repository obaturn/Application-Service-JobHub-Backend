-- Database Schema for Application Service
-- Run this SQL to create the necessary tables in your database

-- Create sequence for job IDs
CREATE SEQUENCE IF NOT EXISTS job_id_seq START WITH 1 INCREMENT BY 1;

-- Jobs Table
CREATE TABLE IF NOT EXISTS job (
    id BIGINT PRIMARY KEY DEFAULT nextval('job_id_seq'),
    title VARCHAR(255) NOT NULL,
    company VARCHAR(255),
    company_id VARCHAR(36),
    location VARCHAR(255),
    type VARCHAR(100),
    salary VARCHAR(100),
    description TEXT,
    status VARCHAR(50) DEFAULT 'Draft',
    posted_date DATE,
    employer_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    applications_count INTEGER DEFAULT 0,
    views_count INTEGER DEFAULT 0,
    seniority VARCHAR(50),
    logo VARCHAR(500),
    is_remote BOOLEAN DEFAULT FALSE,
    education_required VARCHAR(255)
);

-- Job responsibilities (element collection)
CREATE TABLE IF NOT EXISTS job_responsibilities (
    job_id BIGINT NOT NULL,
    responsibilities TEXT,
    FOREIGN KEY (job_id) REFERENCES job(id) ON DELETE CASCADE
);

-- Job skills (element collection)
CREATE TABLE IF NOT EXISTS job_skills (
    job_id BIGINT NOT NULL,
    skills TEXT,
    FOREIGN KEY (job_id) REFERENCES job(id) ON DELETE CASCADE
);

-- Job benefits (element collection)
CREATE TABLE IF NOT EXISTS job_benefits (
    job_id BIGINT NOT NULL,
    benefits TEXT,
    FOREIGN KEY (job_id) REFERENCES job(id) ON DELETE CASCADE
);

-- Applications Table
CREATE TABLE IF NOT EXISTS applications (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    job_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL,
    applied_date DATE NOT NULL,
    resume_id VARCHAR(36),
    resume_data BYTEA,
    resume_file_name VARCHAR(255),
    resume_content_type VARCHAR(100),
    cover_letter TEXT,
    rejection_reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    withdrawn_date TIMESTAMP NULL,
    withdraw_reason TEXT,

    UNIQUE KEY unique_user_job (user_id, job_id),
    INDEX idx_user_status (user_id, status),
    INDEX idx_applied_date (applied_date),
    INDEX idx_job_id (job_id),
    INDEX idx_job_status (job_id, status)
);

-- Saved Jobs Table
CREATE TABLE IF NOT EXISTS saved_jobs (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    job_id BIGINT NOT NULL,
    saved_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY unique_user_saved_job (user_id, job_id),
    INDEX idx_user_saved_date (user_id, saved_date)
);

-- Recommendation Feedback Table
CREATE TABLE IF NOT EXISTS recommendation_feedback (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    job_id BIGINT NOT NULL,
    feedback VARCHAR(50) NOT NULL,
    reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_user_feedback (user_id, feedback)
);

-- Recommendation Cache Table
CREATE TABLE IF NOT EXISTS recommendation_cache (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    job_id BIGINT NOT NULL,
    match_score INT NOT NULL,
    match_reasons TEXT,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_user_score (user_id, match_score DESC),
    INDEX idx_expires (expires_at)
);

-- Sample test data for development

-- Insert sample applications for test-user-123
INSERT INTO applications (id, user_id, job_id, status, applied_date, resume_id, created_at, updated_at) VALUES
('app-001', 'test-user-123', 1, 'APPLIED', CURDATE(), 'resume-001', NOW(), NOW()),
('app-002', 'test-user-123', 2, 'IN_REVIEW', DATE_SUB(CURDATE(), INTERVAL 5 DAY), 'resume-001', DATE_SUB(NOW(), INTERVAL 5 DAY), NOW()),
('app-003', 'test-user-123', 3, 'INTERVIEW', DATE_SUB(CURDATE(), INTERVAL 10 DAY), 'resume-001', DATE_SUB(NOW(), INTERVAL 10 DAY), NOW()),
('app-004', 'test-user-123', 4, 'SHORTLISTED', DATE_SUB(CURDATE(), INTERVAL 3 DAY), 'resume-001', DATE_SUB(NOW(), INTERVAL 3 DAY), NOW()),
('app-005', 'test-user-123', 5, 'OFFERED', DATE_SUB(CURDATE(), INTERVAL 15 DAY), 'resume-001', DATE_SUB(NOW(), INTERVAL 15 DAY), NOW());

-- Insert sample saved jobs for test-user-123
INSERT INTO saved_jobs (id, user_id, job_id, saved_date) VALUES
('saved-001', 'test-user-123', 6, NOW()),
('saved-002', 'test-user-123', 7, DATE_SUB(NOW(), INTERVAL 2 DAY)),
('saved-003', 'test-user-123', 8, DATE_SUB(NOW(), INTERVAL 5 DAY));

-- Insert sample recommendation feedback for test-user-123
INSERT INTO recommendation_feedback (id, user_id, job_id, feedback, reason, created_at) VALUES
('fb-001', 'test-user-123', 1, 'RELEVANT', 'Good match for my skills', NOW()),
('fb-002', 'test-user-123', 2, 'NOT_INTERESTED', 'Location not suitable', NOW());
