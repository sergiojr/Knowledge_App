-- Table: sentence_word_relation_history

-- DROP TABLE sentence_word_relation_history;

CREATE TABLE sentence_word_relation_history
(
  version_id integer NOT NULL,
  source_id integer NOT NULL,
  sentence_id integer NOT NULL,
  relation_type integer NOT NULL,
  word_pos integer NOT NULL,
  preposition text,
  word text NOT NULL,
  type integer NOT NULL,
  dep_word_pos integer NOT NULL,
  dep_preposition text,
  dep_word text NOT NULL,
  dep_type integer NOT NULL,
  error boolean NOT NULL,
  CONSTRAINT sentence_word_relation_history_pkey PRIMARY KEY (version_id , source_id , sentence_id , relation_type , word_pos , dep_word_pos )
)
WITH (
  OIDS=FALSE
);
ALTER TABLE sentence_word_relation_history
  OWNER TO knowledge;
