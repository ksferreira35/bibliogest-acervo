package br.com.bibliogest.acervo_api.service;

import br.com.bibliogest.acervo_api.dto.CategoriaRequest;
import br.com.bibliogest.acervo_api.dto.CategoriaResponse;
import br.com.bibliogest.acervo_api.entities.Categoria;
import br.com.bibliogest.acervo_api.repository.CategoriaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoriaService {
    private final CategoriaRepository repository;

    public CategoriaService(CategoriaRepository repository) {
        this.repository = repository;
    }

    public CategoriaResponse cadastrar(CategoriaRequest request) {
        Categoria categoria = new Categoria(request.nome());
        Categoria salva = repository.save(categoria);
        return new CategoriaResponse(salva.getId(), salva.getNome());
    }

    public List<CategoriaResponse> listar() {
        return repository.findAll()
                .stream()
                .map(CategoriaResponse::new)
                .toList();
    }
}
