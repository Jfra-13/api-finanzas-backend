-- Recovery OTP grows from 4 to 6 digits (10^4 codes was guessable within days
-- even with the 5-attempt lock). SET DATA TYPE is valid in both H2 and PostgreSQL.
ALTER TABLE usuarios ALTER COLUMN codigo_otp SET DATA TYPE VARCHAR(6);
