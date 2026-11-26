package br.com.bibliogest.acervo_api.service;

import br.com.bibliogest.acervo_api.dto.ObraRequest;
import br.com.bibliogest.acervo_api.dto.ObraResponse;
import br.com.bibliogest.acervo_api.dto.PaginaResponse;
import br.com.bibliogest.acervo_api.entities.Autor;
import br.com.bibliogest.acervo_api.entities.Categoria;
import br.com.bibliogest.acervo_api.entities.Obra;
import br.com.bibliogest.acervo_api.repository.AutorRepository;
import br.com.bibliogest.acervo_api.repository.CategoriaRepository;
import br.com.bibliogest.acervo_api.repository.ExemplarRepository;
import br.com.bibliogest.acervo_api.repository.ObraFiltros;
import br.com.bibliogest.acervo_api.repository.ObraRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ObraService {
    private final ObraRepository obraRepository;
    private final CategoriaRepository categoriaRepository;
    private final AutorRepository autorRepository;
    private final ExemplarRepository exemplarRepository;
    private final ArmazenamentoDeCapas capas;

    public ObraService(
            ObraRepository obraRepository,
            CategoriaRepository categoriaRepository,
            AutorRepository autorRepository,
            ExemplarRepository exemplarRepository,
            ArmazenamentoDeCapas capas
    ) {
        this.obraRepository = obraRepository;
        this.categoriaRepository = categoriaRepository;
        this.autorRepository = autorRepository;
        this.exemplarRepository = exemplarRepository;
        this.capas = capas;
    }

    @Transactional
    public ObraResponse cadastrar(ObraRequest request) {
        Categoria categoria = categoria(request);
        Set<Autor> autores = autores(request);

        Obra obra = new Obra(
                request.titulo(),
                categoria,
                autores,
                request.volume(),
                request.isbn()
        );
        obra.definirDescricao(request.descricao());
        return new ObraResponse(obraRepository.save(obra));
    }

    @Transactional(readOnly = true)
    public PaginaResponse<ObraResponse> listar(int pagina, int tamanho, String busca, UUID categoriaId) {
        var ordem = Sort.by("titulo").ascending().and(Sort.by("id").ascending());
        var resultado = obraRepository.findAll(ObraFiltros.catalogo(busca, categoriaId),
                PageRequest.of(pagina, tamanho, ordem)).map(ObraResponse::new);
        return PaginaResponse.de(resultado);
    }

    @Transactional(readOnly = true)
    public List<ObraResponse> resumir(Set<UUID> ids) {
        return obraRepository.findDistinctByIdIn(ids).stream().map(ObraResponse::new).toList();
    }

    @Transactional
    public ObraResponse atualizar(UUID id, ObraRequest request) {
        Obra obra = buscarParaAlteracao(id);
        Categoria categoria = categoria(request);
        Set<Autor> autores = autores(request);
        obra.atualizar(request.titulo(), categoria, autores, request.volume(), request.isbn());
        obra.definirDescricao(request.descricao());
        return new ObraResponse(obra);
    }

    @Transactional
    public void excluir(UUID id) {
        Obra obra = buscarParaAlteracao(id);
        if (exemplarRepository.existsByObraId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esta obra possui exemplares vinculados e não pode ser excluída.");
        }
        String capa = obra.getCapaChave();
        obraRepository.delete(obra);
        obraRepository.flush();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                capas.remover(capa);
            }
        });
    }

    private Obra buscarParaAlteracao(UUID id) {
        return obraRepository.findForUpdateById(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Obra informada não encontrada"));
    }

    private Categoria categoria(ObraRequest request) {
        return categoriaRepository.findById(request.categoriaId()).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Categoria informada não existe"));
    }

    private Set<Autor> autores(ObraRequest request) {
        List<Autor> autores = autorRepository.findAllById(request.autorIds());
        if (autores.size() != request.autorIds().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Um ou mais autores informados não existem");
        }
        return new HashSet<>(autores);
    }

    @Transactional(readOnly = true)
    public ObraResponse buscarPorUUID(UUID id) {
        Obra obra = obraRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Obra informada não encontrada"
                ));

        return new ObraResponse(obra);
    }
}
