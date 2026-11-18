package br.com.bibliogest.acervo_api.dto;

import br.com.bibliogest.acervo_api.entities.Exemplar;

import java.util.UUID;

public record ExemplarResponse(UUID id, UUID obraId, String codigoTombo) {
    public ExemplarResponse(Exemplar exemplar) {
        this(
                exemplar.getId(),
                exemplar.getObra().getId(),
                exemplar.getCodigoTombo()
        );
    }
}
