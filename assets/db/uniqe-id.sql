-- Table: report.unique_id

-- DROP TABLE report.unique_id;

CREATE TABLE report.unique_id
(
  id integer NOT NULL DEFAULT nextval('report.unique_id_id_seq'::regclass),
  anm_id integer,
  last_value bigint,
  CONSTRAINT pk_unique_id PRIMARY KEY (id),
  CONSTRAINT fk_id_anm_dim_anm FOREIGN KEY (anm_id)
      REFERENCES report.dim_anm (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION
)
WITH (
  OIDS=FALSE
);
ALTER TABLE report.unique_id
  OWNER TO postgres;
