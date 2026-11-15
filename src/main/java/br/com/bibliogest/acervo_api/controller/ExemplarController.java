package br.com.bibliogest.acervo_api.controller;

import br.com.bibliogest.acervo_api.dto.ExemplarRequest;
import br.com.bibliogest.acervo_api.dto.ExemplarResponse;
import br.com.bibliogest.acervo_api.service.ExemplarService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/obras/{obraId}/exemplares")
@Tag(name = "Exemplares", description = "Endpoints para gerenciamento de exemplares")
public class ExemplarController {
    private final ExemplarService service;

    public ExemplarController(ExemplarService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('BIBLIOTECARIO')")
    public ExemplarResponse cadastrar(
            @PathVariable("obraId") UUID obraId,
            @Valid @RequestBody ExemplarRequest request
    ) {
        return service.cadastrar(obraId, request);
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<ExemplarResponse> listar(@PathVariable("obraId") UUID obraId) {
        return service.listar(obraId);
    }
}
