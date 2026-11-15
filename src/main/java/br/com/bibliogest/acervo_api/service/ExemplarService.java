package br.com.bibliogest.acervo_api.service;

import br.com.bibliogest.acervo_api.dto.ExemplarRequest;
import br.com.bibliogest.acervo_api.dto.ExemplarResponse;
import br.com.bibliogest.acervo_api.entities.Exemplar;
import br.com.bibliogest.acervo_api.entities.Obra;
import br.com.bibliogest.acervo_api.repository.ExemplarRepository;
import br.com.bibliogest.acervo_api.repository.ObraRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class ExemplarService {
    private final ExemplarRepository exemplarRepository;
    private final ObraRepository obraRepository;

    public ExemplarService(ExemplarRepository exemplarRepository, ObraRepository obraRepository) {
        this.exemplarRepository = exemplarRepository;
        this.obraRepository = obraRepository;
    }

    @Transactional
    public ExemplarResponse cadastrar(UUID obraId, ExemplarRequest request) {
        Obra obra = obraRepository.findById(obraId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Obra informada não encontrada"
                ));

        Exemplar exemplar = new Exemplar(obra, request.codigoTombo());
        Exemplar salvo = exemplarRepository.save(exemplar);

        return new ExemplarResponse(
                salvo.getId(),
                obra.getId(),
                salvo.getCodigoTombo()
        );
    }

    @Transactional(readOnly = true)
    public List<ExemplarResponse> listar(UUID obraId) {
        if (!obraRepository.existsById(obraId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Obra informada não encontrada"
            );
        }

        return exemplarRepository.findByObraId(obraId)
                .stream()
                .map(ExemplarResponse::new)
                .toList();

    }
}
