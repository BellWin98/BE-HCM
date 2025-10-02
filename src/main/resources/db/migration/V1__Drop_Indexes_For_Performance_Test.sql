-- 성능 비교 테스트를 위한 인덱스 제거 스크립트
-- 주의: 이 파일은 성능 테스트 목적으로만 사용됩니다.

-- 기존 인덱스가 있다면 제거 (성능 비교를 위해)
DROP INDEX IF EXISTS idx_workout_record_member_room_date ON workout_record;
DROP INDEX IF EXISTS idx_workout_record_member_created ON workout_record;
DROP INDEX IF EXISTS idx_workout_record_date_range ON workout_record;
DROP INDEX IF EXISTS idx_penalty_room_paid ON penalty;
DROP INDEX IF EXISTS idx_penalty_paid_created ON penalty;
DROP INDEX IF EXISTS idx_penalty_week_dates ON penalty;

DROP INDEX IF EXISTS idx_chat_message_room_id_desc ON chat_message;
DROP INDEX IF EXISTS idx_chat_message_room_id_asc ON chat_message;
DROP INDEX IF EXISTS idx_chat_message_room_timestamp ON chat_message;

DROP INDEX IF EXISTS idx_workout_room_active ON workout_room;
DROP INDEX IF EXISTS idx_workout_room_entry_code ON workout_room;
DROP INDEX IF EXISTS idx_workout_room_active_end_date ON workout_room;

DROP INDEX IF EXISTS idx_workout_room_member_member ON workout_room_member;
DROP INDEX IF EXISTS idx_workout_room_member_room_joined ON workout_room_member;
DROP INDEX IF EXISTS idx_workout_room_member_member_room ON workout_room_member;

DROP INDEX IF EXISTS idx_email_verification_email_verified_created ON email_verification;

DROP INDEX IF EXISTS idx_rest_workout_room_member ON rest;
DROP INDEX IF EXISTS idx_rest_dates ON rest;
