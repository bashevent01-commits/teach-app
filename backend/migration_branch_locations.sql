-- Each branch account says where it trades; head office constituency is on the institution
ALTER TABLE users ADD COLUMN IF NOT EXISTS county VARCHAR(50);
ALTER TABLE users ADD COLUMN IF NOT EXISTS constituency VARCHAR(80);
ALTER TABLE institutions ADD COLUMN IF NOT EXISTS constituency VARCHAR(80);
