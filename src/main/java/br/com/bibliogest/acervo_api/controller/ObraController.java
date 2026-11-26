package br.com.bibliogest.acervo_api.controller;

import br.com.bibliogest.acervo_api.dto.ObraRequest;
import br.com.bibliogest.acervo_api.dto.ObraResponse;
import br.com.bibliogest.acervo_api.dto.PaginaResponse;
import br.com.bibliogest.acervo_api.service.ObraService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
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
    public PaginaResponse<ObraResponse> listar(
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho,
            @RequestParam(required = false) @Size(max = 100) String busca,
            @RequestParam(required = false) UUID categoriaId
    ) {
        return service.listar(pagina, tamanho, busca, categoriaId);
    }

    @GetMapping("/resumo")
    public List<ObraResponse> resumir(@RequestParam @Size(min = 1, max = 100) Set<UUID> ids) {
        return service.resumir(ids);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('BIBLIOTECARIO')")
    public ObraResponse atualizar(@PathVariable UUID id, @Valid @RequestBody ObraRequest request) {
        return service.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('BIBLIOTECARIO')")
    public void excluir(@PathVariable UUID id) {
        service.excluir(id);
    }

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public ObraResponse buscarPorUUID(@PathVariable UUID id) {
        return service.buscarPorUUID(id);
    }
}
