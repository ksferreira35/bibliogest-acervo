package br.com.bibliogest.acervo_api.repository;

import br.com.bibliogest.acervo_api.entities.Obra;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface ObraRepository extends JpaRepository<Obra, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Obra> findForUpdateById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<Obra> findForShareById(UUID id);
}
