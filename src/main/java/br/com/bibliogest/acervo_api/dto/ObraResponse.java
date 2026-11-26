package br.com.bibliogest.acervo_api.dto;

import br.com.bibliogest.acervo_api.entities.Obra;

import java.util.List;
import java.util.UUID;

public record ObraResponse(
        UUID id,
        String titulo,
        CategoriaResponse categoria,
        List<AutorResponse> autor,
        String volume,
        String isbn,
        String capaUrl,
        String descricao
) {
    public ObraResponse(Obra obra) {
        this(
                obra.getId(),
                obra.getTitulo(),
                new CategoriaResponse(obra.getCategoria()),
                obra.getAutores().stream().map(AutorResponse::new).toList(),
                obra.getVolume(),
                obra.getIsbn(),
                obra.getCapaChave() == null ? null : "/acervo/capas/" + obra.getId() + ".jpg",
                obra.getDescricao()
        );
    }
}
