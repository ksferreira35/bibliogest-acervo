package br.com.bibliogest.acervo_api.repository;

import br.com.bibliogest.acervo_api.entities.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CategoriaRepository extends JpaRepository<Categoria, UUID> {}
