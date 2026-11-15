package br.com.bibliogest.acervo_api.dto;

import br.com.bibliogest.acervo_api.entities.Autor;

import java.util.UUID;

public record AutorResponse(UUID id, String nome) {
    public AutorResponse(Autor autor) {
        this(autor.getId(), autor.getNome());
    }
}
