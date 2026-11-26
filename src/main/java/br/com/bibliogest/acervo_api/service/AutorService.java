package br.com.bibliogest.acervo_api.service;

import br.com.bibliogest.acervo_api.dto.AutorRequest;
import br.com.bibliogest.acervo_api.dto.AutorResponse;
import br.com.bibliogest.acervo_api.entities.Autor;
import br.com.bibliogest.acervo_api.repository.AutorRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AutorService {
    private final AutorRepository repository;

    public AutorService(AutorRepository repository) {
        this.repository = repository;
    }

    public AutorResponse cadastrar(AutorRequest request) {
        Autor autor = new Autor(request.nome());
        Autor salvo = repository.save(autor);
        return new AutorResponse(salvo.getId(), salvo.getNome());
    }

    public List<AutorResponse> listar() {
        return repository.findAll()
                .stream()
                .map(AutorResponse::new)
                .toList();
    }
}
