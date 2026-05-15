-- Cities table with pre-computed TSVECTOR for full-text search.
-- Population-weighted ranking requires the population column; accent-folded search
-- uses unaccent() in the generated column expression.
CREATE TABLE cities (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    geoname_id   BIGINT NOT NULL UNIQUE,
    name         VARCHAR(200) NOT NULL,
    country      VARCHAR(120) NOT NULL,
    country_code CHAR(2) NOT NULL,
    lat          NUMERIC(9,6) NOT NULL,
    lng          NUMERIC(9,6) NOT NULL,
    population   BIGINT NOT NULL,
    search_tsv   TSVECTOR GENERATED ALWAYS AS (
        to_tsvector('simple', unaccent(name) || ' ' || unaccent(country))
    ) STORED
);

-- GIN index on tsvector for full-text search queries
CREATE INDEX cities_search_tsv_idx ON cities USING GIN (search_tsv);

-- Trigram index for fuzzy/LIKE queries (future use)
CREATE INDEX cities_name_trgm_idx ON cities USING GIN (name gin_trgm_ops);

-- B-tree index on country for filtered lookups
CREATE INDEX cities_country_idx ON cities (country);
