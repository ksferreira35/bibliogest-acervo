package br.com.bibliogest.acervo_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record ObraRequest(
        @NotBlank @Size(max = 100) String titulo,
        @NotNull UUID categoriaId,
        @NotEmpty Set<@NotNull UUID> autorIds,
        @Size(max = 50) String volume,
        @Size(max = 30) String isbn
) {}
