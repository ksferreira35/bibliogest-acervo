package br.com.bibliogest.acervo_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AutorRequest(
        @NotBlank
        @Size(max = 100)
        String nome
) {
}
