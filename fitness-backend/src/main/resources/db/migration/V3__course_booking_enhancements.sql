-- Fields used by the completed scheduling and check-in workflow.
ALTER TABLE course_schedule ADD COLUMN IF NOT EXISTS coach_name VARCHAR(50);
ALTER TABLE booking ADD COLUMN IF NOT EXISTS checked_in_at TIMESTAMP;

-- A member may have only one record per schedule. A cancelled record is retained for audit.
CREATE UNIQUE INDEX IF NOT EXISTS uk_booking_schedule_member ON booking(schedule_id, member_id);
