package br.com.bibliogest.acervo_api.controller;

import br.com.bibliogest.acervo_api.dto.ObraRequest;
import br.com.bibliogest.acervo_api.dto.ObraResponse;
import br.com.bibliogest.acervo_api.entities.Obra;
import br.com.bibliogest.acervo_api.service.ObraService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/obras")
@Tag(name = "Obras", description = "Endpoints para gerenciamento de obras")
public class ObraController {
    private final ObraService service;

    public ObraController(ObraService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('BIBLIOTECARIO')")
    public ObraResponse cadastrar(@Valid @RequestBody ObraRequest request) {
        return service.cadastrar(request);
    }


    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<ObraResponse> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public ObraResponse buscarPorUUID(@PathVariable UUID id) {
        return service.buscarPorUUID(id);
    }
}