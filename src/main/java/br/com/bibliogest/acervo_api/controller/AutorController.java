package br.com.bibliogest.acervo_api.controller;

import br.com.bibliogest.acervo_api.dto.AutorRequest;
import br.com.bibliogest.acervo_api.dto.AutorResponse;
import br.com.bibliogest.acervo_api.service.AutorService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/autores")
@Tag(name = "Autores", description = "Endpoints para gerenciamento de autores")
public class AutorController {
    private final AutorService service;

    public AutorController(AutorService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('BIBLIOTECARIO')")
    public AutorResponse cadastrar(@Valid @RequestBody AutorRequest request) {
        return service.cadastrar(request);
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<AutorResponse> listar() {
        return service.listar();
    }
}
