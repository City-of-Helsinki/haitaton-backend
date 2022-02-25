--liquibase formatted sql
--changeset janne:008-suunnitteluvaihe-enumtype-string
--comment: Add EnumType.STRING for SuunnitteluVaihe

UPDATE hanke
SET suunnitteluvaihe =
CASE
  WHEN suunnitteluvaihe = '0' THEN 'YLEIS_TAI_HANKE'
  WHEN suunnitteluvaihe = '1' THEN 'KATUSUUNNITTELU_TAI_ALUEVARAUS'
  WHEN suunnitteluvaihe = '2' THEN 'RAKENNUS_TAI_TOTEUTUS'
  WHEN suunnitteluvaihe = '3' THEN 'TYOMAAN_TAI_HANKKEEN_AIKAINEN'
  ELSE NULL
END;
