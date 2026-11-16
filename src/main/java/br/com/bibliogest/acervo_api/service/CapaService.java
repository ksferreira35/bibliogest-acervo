package br.com.bibliogest.acervo_api.service;

import br.com.bibliogest.acervo_api.dto.ObraResponse;
import br.com.bibliogest.acervo_api.entities.Obra;
import br.com.bibliogest.acervo_api.repository.ObraRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class CapaService {
    private final ObraRepository obras;
    private final ArmazenamentoDeCapas armazenamento;

    public CapaService(ObraRepository obras, ArmazenamentoDeCapas armazenamento) {
        this.obras = obras;
        this.armazenamento = armazenamento;
    }

    @Transactional
    public ObraResponse salvar(UUID obraId, MultipartFile arquivo) {
        Obra obra = obras.findForUpdateById(obraId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Obra informada não encontrada"));
        String anterior = obra.getCapaChave();
        String nova = armazenamento.salvar(arquivo);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    armazenamento.remover(anterior);
                } else if (status == STATUS_ROLLED_BACK) {
                    armazenamento.remover(nova);
                }
            }
        });

        obra.definirCapa(nova);
        return new ObraResponse(obra);
    }

    @Transactional
    public byte[] buscar(UUID obraId) {
        String chave = obras.findForShareById(obraId)
                .map(Obra::getCapaChave)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Capa não encontrada"));
        return armazenamento.carregar(chave);
    }
}
