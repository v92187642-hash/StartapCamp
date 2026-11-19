-- Only a BCrypt hash is kept. Null for an account made before passwords
-- existed (login was by a one-time email code); such an account cannot log in.
alter table users add column password_hash text;
