-- Performance optimization indexes for HCM application
-- Priority: HIGH - WorkoutRecord table indexes (most critical for penalty calculation)

-- 1. WorkoutRecord table indexes
CREATE INDEX IF NOT EXISTS idx_workout_record_member_room_date
ON workout_record (member_id, workout_room_id, workout_date);

CREATE INDEX IF NOT EXISTS idx_workout_record_member_created
ON workout_record (member_id, created_at);

CREATE INDEX IF NOT EXISTS idx_workout_record_date_range
ON workout_record (workout_date);

-- Priority: HIGH - Penalty table indexes (critical for payment features)

-- 2. Penalty table indexes
CREATE INDEX IF NOT EXISTS idx_penalty_room_paid
ON penalty (workout_room_member_id, is_paid);

CREATE INDEX IF NOT EXISTS idx_penalty_paid_created
ON penalty (is_paid, created_at);

CREATE INDEX IF NOT EXISTS idx_penalty_week_dates
ON penalty (week_start_date, week_end_date);

-- Priority: HIGH - ChatMessage table indexes (critical for real-time chat)

-- 3. ChatMessage table indexes
CREATE INDEX IF NOT EXISTS idx_chat_message_room_id_desc
ON chat_message (workout_room_id, id DESC);

CREATE INDEX IF NOT EXISTS idx_chat_message_room_id_asc
ON chat_message (workout_room_id, id ASC);

CREATE INDEX IF NOT EXISTS idx_chat_message_room_timestamp
ON chat_message (workout_room_id, timestamp);

-- Priority: MEDIUM - WorkoutRoom table indexes

-- 4. WorkoutRoom table indexes
CREATE INDEX IF NOT EXISTS idx_workout_room_active
ON workout_room (is_active);

CREATE INDEX IF NOT EXISTS idx_workout_room_entry_code
ON workout_room (entry_code);

CREATE INDEX IF NOT EXISTS idx_workout_room_active_end_date
ON workout_room (is_active, end_date);

-- Priority: MEDIUM - WorkoutRoomMember table indexes

-- 5. WorkoutRoomMember table indexes
CREATE INDEX IF NOT EXISTS idx_workout_room_member_member
ON workout_room_member (member_id);

CREATE INDEX IF NOT EXISTS idx_workout_room_member_room_joined
ON workout_room_member (workout_room_id, joined_at);

CREATE INDEX IF NOT EXISTS idx_workout_room_member_member_room
ON workout_room_member (member_id, workout_room_id);

-- Priority: LOW - EmailVerification table indexes

-- 6. EmailVerification table indexes
CREATE INDEX IF NOT EXISTS idx_email_verification_email_verified_created
ON email_verification (email, is_verified, created_at DESC);

-- Priority: LOW - Rest table indexes

-- 7. Rest table indexes
CREATE INDEX IF NOT EXISTS idx_rest_workout_room_member
ON rest (workout_room_member_id);

CREATE INDEX IF NOT EXISTS idx_rest_dates
ON rest (start_date, end_date);