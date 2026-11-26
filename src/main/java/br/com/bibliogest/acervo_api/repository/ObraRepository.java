package br.com.bibliogest.acervo_api.repository;

import br.com.bibliogest.acervo_api.entities.Obra;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ObraRepository extends JpaRepository<Obra, UUID>, JpaSpecificationExecutor<Obra> {
    @Override
    @EntityGraph(attributePaths = "categoria")
    Page<Obra> findAll(Specification<Obra> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"categoria", "autores"})
    List<Obra> findDistinctByIdIn(Set<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Obra> findForUpdateById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<Obra> findForShareById(UUID id);
}
