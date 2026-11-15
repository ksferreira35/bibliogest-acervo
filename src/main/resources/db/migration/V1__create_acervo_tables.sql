CREATE TABLE categoria (
    id UUID PRIMARY KEY,
    nome VARCHAR(100) NOT NULL
);

CREATE TABLE autor (
    id UUID PRIMARY KEY,
    nome VARCHAR(100) NOT NULL
);

CREATE TABLE obra (
    id UUID PRIMARY KEY,
    titulo VARCHAR(100) NOT NULL,
    categoria_id UUID NOT NULL REFERENCES categoria(id),
    volume VARCHAR(50),
    isbn VARCHAR(30)
);

CREATE TABLE obra_autor (
    obra_id UUID NOT NULL REFERENCES obra(id),
    autor_id UUID NOT NULL REFERENCES autor(id),
    PRIMARY KEY (obra_id, autor_id)
);

CREATE TABLE exemplar (
    id UUID PRIMARY KEY,
    obra_id UUID NOT NULL REFERENCES obra(id),
    codigo_tombo VARCHAR(100) NOT NULL UNIQUE
);