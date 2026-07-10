-- Optional contact phone for the user profile (GET/PUT /usuarios/me).
-- Nullable on purpose: existing accounts have no phone until the user sets one.
ALTER TABLE usuarios ADD COLUMN telefono VARCHAR(20);
