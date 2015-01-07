-- Table: fixed_words

-- DROP TABLE fixed_words;

CREATE TABLE fixed_words
(
  word text,
  type integer,
  base_form text,
  wcase integer DEFAULT 0,
  gender integer DEFAULT 0,
  sing_pl integer DEFAULT 0,
  person integer DEFAULT 0,
  only_form integer NOT NULL DEFAULT 0,
  rule_id integer NOT NULL DEFAULT nextval('fixed_words_id'::regclass),
  subtype integer NOT NULL DEFAULT 0,
  animate integer DEFAULT 0,
  separator integer NOT NULL DEFAULT 0,
  CONSTRAINT fixed_words_pkey PRIMARY KEY (rule_id)
)
WITH (
  OIDS=FALSE
);
ALTER TABLE fixed_words
  OWNER TO knowledge;

-- Index: fixed_index_02

-- DROP INDEX fixed_index_02;

CREATE INDEX fixed_index_02
  ON fixed_words
  USING btree
  (word COLLATE pg_catalog."default");


