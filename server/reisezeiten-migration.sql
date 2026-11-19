-- fXtime: Reiseland für Reisezeiten (TRAVEL_START/TRAVEL_END), 2026-11
-- VOR dem Hochladen der neuen timeevent.php/getevents.php ausführen.

-- 1) Spalte für das Reiseland (ISO-Code, z. B. AT, DE)
ALTER TABLE tblworktimeraw ADD COLUMN country CHAR(2) NULL AFTER event;

-- 2) Prüfen, ob 'event' lang genug für 'TRAVEL_START' (12 Zeichen) ist
SHOW COLUMNS FROM tblworktimeraw LIKE 'event';
-- falls kürzer als 12 Zeichen:
-- ALTER TABLE tblworktimeraw MODIFY event VARCHAR(20) NOT NULL;
