-- Table: ending_rules

-- DROP TABLE ending_rules;

CREATE TABLE ending_rules
(
  rule_no integer,
  type integer NOT NULL,
  ending text NOT NULL DEFAULT ''::text,
  wcase integer,
  gender integer,
  person integer,
  sing_pl integer,
  tense integer,
  allow_after text,
  deny_after text,
  e_before text,
  o_before text,
  postfix integer,
  min_length integer,
  rule_id integer NOT NULL DEFAULT nextval('ending_rules_id'::regclass),
  subtype integer NOT NULL DEFAULT 0,
  rule_variance integer NOT NULL DEFAULT 0,
  animate integer DEFAULT 0,
  CONSTRAINT ending_rules_pkey PRIMARY KEY (rule_id )
)
WITH (
  OIDS=FALSE
);
ALTER TABLE ending_rules
  OWNER TO knowledge;

-- Index: fixed_index01

-- DROP INDEX fixed_index01;

CREATE INDEX fixed_index01
  ON ending_rules
  USING btree
  (ending COLLATE pg_catalog."default" );

-- Index: fixed_index02

-- DROP INDEX fixed_index02;

CREATE INDEX fixed_index02
  ON ending_rules
  USING btree
  (rule_no , tense , sing_pl , wcase , gender , person );

