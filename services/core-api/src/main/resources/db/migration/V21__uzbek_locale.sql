-- identity: узбекский язык интерфейса (uz). Ограничение из V2 допускало только ru и en.
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_locale_check;
ALTER TABLE users ADD CONSTRAINT users_locale_check CHECK (locale IN ('ru', 'en', 'uz'));
