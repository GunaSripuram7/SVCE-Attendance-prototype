-- Supabase Database Schema for Attendance System
-- Run this in your Supabase SQL Editor

-- Enable necessary extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Attendance Sessions Table
CREATE TABLE IF NOT EXISTS attendance_sessions (
    id BIGSERIAL PRIMARY KEY,
    session_uuid UUID DEFAULT uuid_generate_v4() UNIQUE NOT NULL,
    session_code VARCHAR(50) UNIQUE NOT NULL,
    teacher_id VARCHAR(255) NOT NULL,
    class_id VARCHAR(100),
    status VARCHAR(50) DEFAULT 'helpers_selection' CHECK (status IN ('helpers_selection', 'active', 'grace_period', 'completed', 'archived')),
    max_helpers INTEGER DEFAULT 25,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    scanning_ended_at TIMESTAMP WITH TIME ZONE,
    final_count INTEGER,
    unique_students TEXT[], -- Array of final roll numbers
    completed_at TIMESTAMP WITH TIME ZONE
);

-- Session Helpers Table
CREATE TABLE IF NOT EXISTS session_helpers (
    id BIGSERIAL PRIMARY KEY,
    session_code VARCHAR(50) REFERENCES attendance_sessions(session_code) ON DELETE CASCADE,
    helper_roll_number VARCHAR(50) NOT NULL,
    status VARCHAR(50) DEFAULT 'assigned' CHECK (status IN ('assigned', 'active', 'completed')),
    device_id VARCHAR(255),
    joined_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(session_code, helper_roll_number)
);

-- Attendance Records Table
CREATE TABLE IF NOT EXISTS attendance_records (
    id BIGSERIAL PRIMARY KEY,
    session_code VARCHAR(50) REFERENCES attendance_sessions(session_code) ON DELETE CASCADE,
    student_roll_number VARCHAR(50) NOT NULL,
    scanned_by_helper VARCHAR(50) NOT NULL,
    scanned_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    scanner_device_id VARCHAR(255),
    is_manual_entry BOOLEAN DEFAULT FALSE,
    UNIQUE(session_code, student_roll_number) -- Prevent duplicate attendance
);

-- Indexes for better performance
CREATE INDEX IF NOT EXISTS idx_attendance_sessions_teacher ON attendance_sessions(teacher_id);
CREATE INDEX IF NOT EXISTS idx_attendance_sessions_status ON attendance_sessions(status);
CREATE INDEX IF NOT EXISTS idx_attendance_sessions_date ON attendance_sessions(created_at);
CREATE INDEX IF NOT EXISTS idx_session_helpers_session ON session_helpers(session_code);
CREATE INDEX IF NOT EXISTS idx_session_helpers_roll ON session_helpers(helper_roll_number);
CREATE INDEX IF NOT EXISTS idx_attendance_records_session ON attendance_records(session_code);
CREATE INDEX IF NOT EXISTS idx_attendance_records_student ON attendance_records(student_roll_number);
CREATE INDEX IF NOT EXISTS idx_attendance_records_helper ON attendance_records(scanned_by_helper);

-- Row Level Security (RLS) Policies
ALTER TABLE attendance_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE session_helpers ENABLE ROW LEVEL SECURITY;
ALTER TABLE attendance_records ENABLE ROW LEVEL SECURITY;

-- Policy: Teachers can manage their own sessions
CREATE POLICY "Teachers can manage their sessions" ON attendance_sessions
    FOR ALL USING (teacher_id = current_user::text);

-- Policy: Helpers can view sessions they're part of
CREATE POLICY "Helpers can view their sessions" ON session_helpers
    FOR SELECT USING (helper_roll_number = current_user::text);

-- Policy: Anyone can insert attendance records (for helpers)
CREATE POLICY "Anyone can insert attendance" ON attendance_records
    FOR INSERT WITH CHECK (true);

-- Policy: Teachers can view attendance for their sessions
CREATE POLICY "Teachers can view session attendance" ON attendance_records
    FOR SELECT USING (
        session_code IN (
            SELECT session_code FROM attendance_sessions
            WHERE teacher_id = current_user::text
        )
    );

-- Function to send OneSignal notifications when session completes
CREATE OR REPLACE FUNCTION send_attendance_notifications()
RETURNS TRIGGER AS $$
DECLARE
    session_roll_numbers TEXT[];
BEGIN
    -- Only trigger when session becomes 'completed'
    IF NEW.status = 'completed' AND OLD.status != 'completed' THEN
        -- Get all unique roll numbers for this session
        SELECT ARRAY_AGG(DISTINCT student_roll_number) INTO session_roll_numbers
        FROM attendance_records
        WHERE session_code = NEW.session_code;

        -- Call Edge Function to send notifications
        PERFORM net.http_post(
            url := 'https://your-project.supabase.co/functions/v1/notify-attendance',
            headers := jsonb_build_object(
                'Content-Type', 'application/json',
                'Authorization', 'Bearer ' || current_setting('app.settings.service_role_key', true)
            ),
            body := jsonb_build_object(
                'session_code', NEW.session_code,
                'roll_numbers', session_roll_numbers,
                'teacher_id', NEW.teacher_id,
                'completed_at', NEW.completed_at
            )
        );

        RAISE LOG 'Attendance notifications triggered for session % with % students',
            NEW.session_code, array_length(session_roll_numbers, 1);
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Trigger to automatically send notifications when session completes
CREATE TRIGGER attendance_notification_trigger
    AFTER UPDATE ON attendance_sessions
    FOR EACH ROW
    WHEN (NEW.status = 'completed' AND OLD.status IS DISTINCT FROM 'completed')
    EXECUTE FUNCTION send_attendance_notifications();

-- Function to cleanup old sessions (optional - for maintenance)
CREATE OR REPLACE FUNCTION cleanup_old_sessions()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    -- Delete sessions older than 30 days
    DELETE FROM attendance_sessions
    WHERE created_at < NOW() - INTERVAL '30 days'
    AND status = 'archived';

    GET DIAGNOSTICS deleted_count = ROW_COUNT;

    RAISE LOG 'Cleaned up % old attendance sessions', deleted_count;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Sample data for testing (remove in production)
-- INSERT INTO attendance_sessions (session_code, teacher_id, class_id, status)
-- VALUES ('TEST001', 'teacher@test.com', 'CS101', 'completed');

COMMENT ON TABLE attendance_sessions IS 'Main attendance sessions created by teachers';
COMMENT ON TABLE session_helpers IS 'Students selected as helpers for scanning';
COMMENT ON TABLE attendance_records IS 'Individual attendance records for each student';