-- Soft-delete marker for user accounts (DELETE /usuarios/me).
-- Null = active account. A timestamp starts the 30-day grace period: logging in
-- within it reactivates the account (column goes back to null); after it, the
-- purge job removes the user and all owned data permanently.
ALTER TABLE usuarios ADD COLUMN eliminado_en TIMESTAMP;
