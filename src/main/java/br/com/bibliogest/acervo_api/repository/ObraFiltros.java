package br.com.bibliogest.acervo_api.repository;

import br.com.bibliogest.acervo_api.entities.Obra;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

public final class ObraFiltros {
    private ObraFiltros() {}

    public static Specification<Obra> catalogo(String busca, UUID categoriaId) {
        return (obra, query, builder) -> {
            var filtros = new ArrayList<Predicate>();
            if (categoriaId != null) filtros.add(builder.equal(obra.get("categoria").get("id"), categoriaId));
            if (busca != null && !busca.isBlank()) {
                String termo = "%" + busca.trim().toLowerCase(Locale.ROOT)
                        .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
                var autor = query.subquery(UUID.class);
                var correlacao = autor.correlate(obra);
                var autores = correlacao.join("autores");
                autor.select(correlacao.get("id"))
                        .where(builder.like(builder.lower(autores.get("nome")), termo, '!'));
                filtros.add(builder.or(
                        builder.like(builder.lower(obra.get("titulo")), termo, '!'),
                        builder.like(builder.lower(obra.get("isbn")), termo, '!'),
                        builder.like(builder.lower(obra.get("categoria").get("nome")), termo, '!'),
                        builder.exists(autor)));
            }
            return builder.and(filtros.toArray(Predicate[]::new));
        };
    }
}
