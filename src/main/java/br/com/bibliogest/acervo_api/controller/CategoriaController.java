package br.com.bibliogest.acervo_api.controller;

import br.com.bibliogest.acervo_api.dto.CategoriaRequest;
import br.com.bibliogest.acervo_api.dto.CategoriaResponse;
import br.com.bibliogest.acervo_api.service.CategoriaService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categorias")
@Tag(name = "Categorias", description = "Endpoints para gerenciamento de categorias")
public class CategoriaController {
    private final CategoriaService service;

    public CategoriaController(CategoriaService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('BIBLIOTECARIO')")
    public CategoriaResponse cadastrar(@Valid @RequestBody CategoriaRequest request) {
        return service.cadastrar(request);
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<CategoriaResponse> listar() {
        return service.listar();
    }
}
