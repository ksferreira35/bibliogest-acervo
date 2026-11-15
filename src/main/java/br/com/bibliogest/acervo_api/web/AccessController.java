package br.com.bibliogest.acervo_api.web;

import java.util.Map;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Accesses", description = "Endpoints sem efeitos colaterais para verificar a integração de autenticação.")
public class AccessController {
    @GetMapping("/api/status")
    Map<String, String> status(Authentication authentication) {
        return Map.of("application", "acervo-api", "subject", authentication.getName());
    }

    @GetMapping("/api/admin/status")
    @PreAuthorize("hasRole('BIBLIOTECARIO')")
    public Map<String, String> adminStatus() {
        return Map.of("application", "acervo-api", "access", "BIBLIOTECARIO");
    }
}
