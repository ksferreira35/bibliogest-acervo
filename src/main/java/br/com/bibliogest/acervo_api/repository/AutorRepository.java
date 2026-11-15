package br.com.bibliogest.acervo_api.repository;

import br.com.bibliogest.acervo_api.entities.Autor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AutorRepository extends JpaRepository<Autor, UUID> {}
