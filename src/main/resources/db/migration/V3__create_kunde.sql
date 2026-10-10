-- Kunden. Die E-Mail ist unabhängig von Gross-/Kleinschreibung eindeutig,
-- deshalb ein Unique-Index auf lower(email) statt einer normalen UNIQUE-Constraint.
CREATE TABLE kunde (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name     VARCHAR(100) NOT NULL,
    email    VARCHAR(254) NOT NULL,
    gesperrt BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_kunde_email CHECK (email LIKE '%_@_%')
);

CREATE UNIQUE INDEX uq_kunde_email ON kunde (lower(email));
