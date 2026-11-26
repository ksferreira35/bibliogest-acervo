ALTER TABLE obra ADD COLUMN descricao TEXT;
ALTER TABLE obra ADD CONSTRAINT ck_obra_descricao_tamanho
    CHECK (descricao IS NULL OR char_length(descricao) <= 5000);
