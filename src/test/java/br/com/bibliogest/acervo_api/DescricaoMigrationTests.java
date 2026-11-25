package br.com.bibliogest.acervo_api;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DescricaoMigrationTests {
    @Test
    void migraAcervoExistenteSemAlterarObrasAutoresCapasOuExemplares() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:descricao-upgrade;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(source).target("2").load().migrate();
        var jdbc = new JdbcTemplate(source);
        UUID categoria = UUID.randomUUID();
        UUID autor = UUID.randomUUID();
        UUID obra = UUID.randomUUID();
        UUID exemplar = UUID.randomUUID();
        jdbc.update("INSERT INTO categoria (id, nome) VALUES (?, ?)", categoria, "Mangá");
        jdbc.update("INSERT INTO autor (id, nome) VALUES (?, ?)", autor, "Sui Ishida");
        jdbc.update("INSERT INTO obra (id, titulo, categoria_id, volume, capa_chave) VALUES (?, ?, ?, ?, ?)",
                obra, "Tokyo Ghoul", categoria, "1", "capas/original.jpg");
        jdbc.update("INSERT INTO obra_autor (obra_id, autor_id) VALUES (?, ?)", obra, autor);
        jdbc.update("INSERT INTO exemplar (id, obra_id, codigo_tombo) VALUES (?, ?, ?)", exemplar, obra, "TG-ORIGINAL");

        Flyway.configure().dataSource(source).load().migrate();

        assertThat(jdbc.queryForObject("SELECT descricao FROM obra WHERE id = ?", String.class, obra)).isNull();
        assertThat(jdbc.queryForObject("SELECT capa_chave FROM obra WHERE id = ?", String.class, obra))
                .isEqualTo("capas/original.jpg");
        assertThat(jdbc.queryForObject("SELECT autor_id FROM obra_autor WHERE obra_id = ?", UUID.class, obra)).isEqualTo(autor);
        assertThat(jdbc.queryForObject("SELECT obra_id FROM exemplar WHERE id = ?", UUID.class, exemplar)).isEqualTo(obra);
        jdbc.update("UPDATE obra SET descricao = ? WHERE id = ?", "Sinopse cadastrada depois da migração.", obra);
        assertThat(jdbc.queryForObject("SELECT descricao FROM obra WHERE id = ?", String.class, obra))
                .isEqualTo("Sinopse cadastrada depois da migração.");
        assertThatThrownBy(() -> jdbc.update("UPDATE obra SET descricao = ? WHERE id = ?", "a".repeat(5001), obra))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
