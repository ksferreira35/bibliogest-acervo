package br.com.bibliogest.acervo_api.service;

import br.com.bibliogest.acervo_api.dto.AutorResponse;
import br.com.bibliogest.acervo_api.dto.CategoriaResponse;
import br.com.bibliogest.acervo_api.dto.ObraRequest;
import br.com.bibliogest.acervo_api.dto.ObraResponse;
import br.com.bibliogest.acervo_api.entities.Autor;
import br.com.bibliogest.acervo_api.entities.Categoria;
import br.com.bibliogest.acervo_api.entities.Obra;
import br.com.bibliogest.acervo_api.repository.AutorRepository;
import br.com.bibliogest.acervo_api.repository.CategoriaRepository;
import br.com.bibliogest.acervo_api.repository.ObraRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
public class ObraService {
    private final ObraRepository obraRepository;
    private final CategoriaRepository categoriaRepository;
    private final AutorRepository autorRepository;

    public ObraService(
            ObraRepository obraRepository,
            CategoriaRepository categoriaRepository,
            AutorRepository autorRepository
    ) {
        this.obraRepository = obraRepository;
        this.categoriaRepository = categoriaRepository;
        this.autorRepository = autorRepository;
    }

    @Transactional
    public ObraResponse cadastrar(ObraRequest request) {
        Categoria categoria = categoriaRepository.findById(request.categoriaId())
                .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Categoria informada não existe"
        ));

        List<Autor> autores = autorRepository.findAllById(request.autorIds());

        if (autores.size() != request.autorIds().size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Um ou mais autores informados não existem"
            );
        }

        Obra obra = new Obra(
                request.titulo(),
                categoria,
                new HashSet<>(autores),
                request.volume(),
                request.isbn()
        );
        Obra salva = obraRepository.save(obra);

        return new ObraResponse(
                salva.getId(),
                salva.getTitulo(),
                new CategoriaResponse(salva.getCategoria()),
                salva.getAutores().stream().map(AutorResponse::new).toList(),
                salva.getVolume(),
                salva.getIsbn()
        );
    }

    @Transactional(readOnly = true)
    public List<ObraResponse> listar() {
        return obraRepository.findAll()
                .stream()
                .map(ObraResponse::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public ObraResponse buscarPorUUID(UUID id) {
        Obra obra = obraRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Obra informada não encontrada"));

        return new ObraResponse(obra);
    }
}
