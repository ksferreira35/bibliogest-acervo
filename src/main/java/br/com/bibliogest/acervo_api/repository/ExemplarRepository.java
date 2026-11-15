package br.com.bibliogest.acervo_api.repository;

import br.com.bibliogest.acervo_api.entities.Exemplar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExemplarRepository extends JpaRepository<Exemplar, UUID> {
    List<Exemplar> findByObraId(UUID obraId);
}
