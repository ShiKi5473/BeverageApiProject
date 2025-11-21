-- V3__add_shedlock_table.sql
-- 建立 ShedLock 專用的鎖定表

CREATE TABLE public.shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

-- 註解：ShedLock 會自動管理這張表的內容，不需要我們手動維護