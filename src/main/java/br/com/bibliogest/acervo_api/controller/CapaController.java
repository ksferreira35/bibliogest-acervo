package br.com.bibliogest.acervo_api.controller;

import br.com.bibliogest.acervo_api.dto.ObraResponse;
import br.com.bibliogest.acervo_api.service.CapaService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@Tag(name = "Capas", description = "Envio e consulta de capas das obras")
public class CapaController {
    private final CapaService service;

    public CapaController(CapaService service) {
        this.service = service;
    }

    @PutMapping(path = "/api/v1/obras/{obraId}/capa", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('BIBLIOTECARIO')")
    public ObraResponse enviar(@PathVariable UUID obraId, @RequestPart("arquivo") MultipartFile arquivo) {
        return service.salvar(obraId, arquivo);
    }

    @GetMapping("/capas/{obraId}.jpg")
    public ResponseEntity<byte[]> buscar(@PathVariable UUID obraId) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noStore())
                .body(service.buscar(obraId));
    }
}
