-- Table: sentence_word_relation

-- DROP TABLE sentence_word_relation;

CREATE TABLE sentence_word_relation
(
  id integer NOT NULL,
  dep_id integer NOT NULL DEFAULT 0,
  sentence_id integer NOT NULL,
  word1_pos integer NOT NULL,
  word1_type integer NOT NULL,
  word1_wcase integer NOT NULL,
  word1_gender integer NOT NULL,
  word1_sing_pl integer NOT NULL,
  word2_pos integer NOT NULL,
  word2_type integer NOT NULL,
  word2_wcase integer NOT NULL,
  word2_gender integer NOT NULL,
  word2_sing_pl integer NOT NULL,
  relation_type integer NOT NULL,
  word1_animate integer DEFAULT 0,
  word2_animate integer DEFAULT 0,
  CONSTRAINT sentence_word_relation_pkey PRIMARY KEY (sentence_id , id )
)
WITH (
  OIDS=FALSE
);
ALTER TABLE sentence_word_relation
  OWNER TO knowledge;
