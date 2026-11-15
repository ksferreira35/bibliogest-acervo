package br.com.bibliogest.acervo_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExemplarRequest(
        @NotBlank @Size(max = 100) String codigoTombo
) { }
