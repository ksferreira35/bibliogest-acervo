package br.com.bibliogest.acervo_api.dto;

import br.com.bibliogest.acervo_api.entities.Categoria;

import java.util.UUID;

public record CategoriaResponse(UUID id, String nome) {
    public CategoriaResponse(Categoria categoria) {
        this(categoria.getId(), categoria.getNome());
    }
}
