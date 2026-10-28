-- The TV's name on its sign-in (NULL for a browser), so the owner can see and sign out each TV from the website.
ALTER TABLE sessions ADD COLUMN device TEXT;
