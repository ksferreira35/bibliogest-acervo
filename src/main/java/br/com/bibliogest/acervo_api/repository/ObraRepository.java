package br.com.bibliogest.acervo_api.repository;

import br.com.bibliogest.acervo_api.entities.Obra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ObraRepository extends JpaRepository<Obra, UUID> {}
