package br.com.bibliogest.acervo_api;

import br.com.bibliogest.acervo_api.entities.Autor;
import br.com.bibliogest.acervo_api.entities.Categoria;
import br.com.bibliogest.acervo_api.entities.Exemplar;
import br.com.bibliogest.acervo_api.entities.Obra;
import br.com.bibliogest.acervo_api.repository.AutorRepository;
import br.com.bibliogest.acervo_api.repository.CategoriaRepository;
import br.com.bibliogest.acervo_api.repository.ExemplarRepository;
import br.com.bibliogest.acervo_api.repository.ObraRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:obras-tests;MODE=PostgreSQL}",
        "spring.datasource.username=${TEST_DB_USERNAME:sa}", "spring.datasource.password=${TEST_DB_PASSWORD:}",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://login.example.test/realms/bibliogest",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://login.example.test/certs"
})
@AutoConfigureMockMvc
@Transactional
class ObraApiTests {
    @Autowired MockMvc mvc;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;
    @Autowired ObraRepository obras;
    @Autowired AutorRepository autores;
    @Autowired CategoriaRepository categorias;
    @Autowired ExemplarRepository exemplares;
    @MockitoBean JwtDecoder decoder;
    Categoria categoria;
    Autor autor;

    @BeforeEach
    void preparar() {
        categoria = categorias.save(new Categoria("Mangá"));
        autor = autores.save(new Autor("Sui Ishida"));
        for (String role : List.of("LEITOR", "BIBLIOTECARIO")) {
            when(decoder.decode(role)).thenReturn(Jwt.withTokenValue(role).header("alg", "RS256")
                    .subject("usuario").claim("realm_access", Map.of("roles", List.of(role))).build());
        }
    }

    @Test
    void bibliotecarioCorrigeAutorSemTrocarIdCapaOuExemplares() throws Exception {
        Obra obra = criar("The Boxer");
        obra.definirCapa("capas/teste.jpg");
        Exemplar exemplar = exemplares.save(new Exemplar(obra, "BOXER-EDIT-1"));
        Autor correto = autores.save(new Autor("Jung Ji-hoon"));
        Categoria outra = categorias.save(new Categoria("Manhwa"));
        mvc.perform(put("/api/v1/obras/{id}", obra.getId()).header("Authorization", "Bearer BIBLIOTECARIO")
                        .contentType(MediaType.APPLICATION_JSON).content(payload("The Boxer", outra.getId(), correto.getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(obra.getId().toString()))
                .andExpect(jsonPath("$.autor[0].nome").value("Jung Ji-hoon"))
                .andExpect(jsonPath("$.categoria.nome").value("Manhwa"))
                .andExpect(jsonPath("$.volume").value(nullValue())).andExpect(jsonPath("$.isbn").value(nullValue()))
                .andExpect(jsonPath("$.capaUrl").value("/acervo/capas/" + obra.getId() + ".jpg"));
        assertThat(exemplares.findByObraId(obra.getId())).extracting(Exemplar::getId).containsExactly(exemplar.getId());
        assertThat(autores.findById(autor.getId())).isPresent();
    }

    @Test
    void leitorNaoPodeEditarNemExcluir() throws Exception {
        Obra obra = criar("Protegida");
        mvc.perform(put("/api/v1/obras/{id}", obra.getId()).header("Authorization", "Bearer LEITOR")
                        .contentType(MediaType.APPLICATION_JSON).content(payload("Alterada", categoria.getId(), autor.getId())))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/obras/{id}", obra.getId()).header("Authorization", "Bearer LEITOR"))
                .andExpect(status().isForbidden());
        assertThat(obras.findById(obra.getId()).orElseThrow().getTitulo()).isEqualTo("Protegida");
    }

    @Test
    void semTokenNaoPodeConsultarEditarOuExcluir() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(get("/api/v1/obras")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/obras/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content(payload("Teste", categoria.getId(), autor.getId()))).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/obras/{id}", id)).andExpect(status().isUnauthorized());
    }

    @Test
    void edicaoInexistenteRetorna404() throws Exception {
        mvc.perform(put("/api/v1/obras/{id}", UUID.randomUUID()).header("Authorization", "Bearer BIBLIOTECARIO")
                        .contentType(MediaType.APPLICATION_JSON).content(payload("Teste", categoria.getId(), autor.getId())))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void edicaoComRelacionamentoInexistenteNaoAlteraObra() throws Exception {
        Obra obra = criar("Original");
        for (String body : List.of(payload("Inválida", UUID.randomUUID(), autor.getId()),
                payload("Inválida", categoria.getId(), UUID.randomUUID()))) {
            mvc.perform(put("/api/v1/obras/{id}", obra.getId()).header("Authorization", "Bearer BIBLIOTECARIO")
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        assertThat(obra.getTitulo()).isEqualTo("Original");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"titulo\":\" \"}", "{\"titulo\":\"Teste\",\"autorIds\":[]}"})
    void edicaoValidaCampos(String body) throws Exception {
        mvc.perform(put("/api/v1/obras/{id}", criar("Original").getId()).header("Authorization", "Bearer BIBLIOTECARIO")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isNotEmpty());
    }

    @Test
    void exclusaoRemoveSomenteObraSemExemplares() throws Exception {
        Obra obra = criar("Descartada");
        mvc.perform(delete("/api/v1/obras/{id}", obra.getId()).header("Authorization", "Bearer BIBLIOTECARIO"))
                .andExpect(status().isNoContent());
        assertThat(obras.existsById(obra.getId())).isFalse();
        assertThat(autores.existsById(autor.getId())).isTrue();
        assertThat(categorias.existsById(categoria.getId())).isTrue();
    }

    @Test
    void exclusaoPreservaObraComExemplarERetorna409() throws Exception {
        Obra obra = criar("Com exemplar");
        exemplares.save(new Exemplar(obra, "NO-DELETE-1"));
        mvc.perform(delete("/api/v1/obras/{id}", obra.getId()).header("Authorization", "Bearer BIBLIOTECARIO"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(
                        "Esta obra possui exemplares vinculados e não pode ser excluída."));
        assertThat(obras.existsById(obra.getId())).isTrue();
        assertThat(exemplares.findByObraId(obra.getId())).hasSize(1);
    }

    @Test
    void excluirInexistenteRetorna404() throws Exception {
        mvc.perform(delete("/api/v1/obras/{id}", UUID.randomUUID()).header("Authorization", "Bearer BIBLIOTECARIO"))
                .andExpect(status().isNotFound());
    }

    @Test
    void paginacaoOrdenaEContaSemDuplicarObrasComVariosAutores() throws Exception {
        Obra beta = criar("Beta");
        Autor segundo = autores.save(new Autor("Outro autor"));
        beta.atualizar("Beta", categoria, Set.of(autor, segundo), "1", null);
        Obra alfa = criar("Alfa");
        criar("Gama");
        mvc.perform(get("/api/v1/obras").param("pagina", "0").param("tamanho", "2")
                        .header("Authorization", "Bearer LEITOR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].id").value(alfa.getId().toString()))
                .andExpect(jsonPath("$.content[1].autor", hasSize(2)))
                .andExpect(jsonPath("$.totalElementos").value(3)).andExpect(jsonPath("$.totalPaginas").value(2));
        mvc.perform(get("/api/v1/obras").param("pagina", "1").param("tamanho", "2")
                        .header("Authorization", "Bearer LEITOR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].titulo").value("Gama"));
    }

    @Test
    void buscaPorAutorTituloIsbnECategoriaFuncionaEmTodasAsPaginas() throws Exception {
        criar("Tokyo Ghoul");
        for (String termo : List.of("sui ISHIDA", "Tokyo", "ISBN-123", "Mangá")) {
            mvc.perform(get("/api/v1/obras").param("busca", termo).header("Authorization", "Bearer LEITOR"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1));
        }
        mvc.perform(get("/api/v1/obras").param("categoriaId", UUID.randomUUID().toString())
                        .header("Authorization", "Bearer LEITOR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void buscaTrataCuringasComoTextoLiteral() throws Exception {
        criar("Título comum");
        criar("100%_real!");
        mvc.perform(get("/api/v1/obras").param("busca", "%_").header("Authorization", "Bearer LEITOR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"pagina=-1", "tamanho=0", "tamanho=101", "pagina=1000001", "pagina=abc", "categoriaId=invalido"})
    void parametrosInvalidosRetornam400(String parametro) throws Exception {
        String[] par = parametro.split("=");
        mvc.perform(get("/api/v1/obras").param(par[0], par[1]).header("Authorization", "Bearer LEITOR"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void paginaForaDoCatalogoRetornaListaVaziaComTotais() throws Exception {
        criar("Única");
        mvc.perform(get("/api/v1/obras").param("pagina", "20").header("Authorization", "Bearer LEITOR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    void resumoBuscaIdsForaDaPaginaSemCarregarCatalogoCompleto() throws Exception {
        Obra primeira = criar("Primeira");
        criar("Ignorada");
        mvc.perform(get("/api/v1/obras/resumo").param("ids", primeira.getId() + "," + UUID.randomUUID())
                        .header("Authorization", "Bearer LEITOR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(primeira.getId().toString()));
    }

    @Test
    void paginaPadraoEConsultasNaoCrescemComCadaAutorOuCategoria() throws Exception {
        for (int index = 0; index < 24; index++) {
            Categoria outra = categorias.save(new Categoria("Categoria " + index));
            Autor outro = autores.save(new Autor("Autor " + index));
            obras.save(new Obra("Obra " + index, outra, Set.of(outro), null, null));
        }
        obras.flush();
        entityManager.clear();
        var estatisticas = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        estatisticas.clear();
        mvc.perform(get("/api/v1/obras").header("Authorization", "Bearer LEITOR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(20)))
                .andExpect(jsonPath("$.tamanho").value(20)).andExpect(jsonPath("$.totalElementos").value(24));
        assertThat(estatisticas.getPrepareStatementCount()).isLessThanOrEqualTo(4);
    }

    @Test
    void resumoRejeitaMaisDe100Ids() throws Exception {
        String ids = java.util.stream.IntStream.range(0, 101).mapToObj(i -> UUID.randomUUID().toString())
                .collect(java.util.stream.Collectors.joining(","));
        mvc.perform(get("/api/v1/obras/resumo").param("ids", ids).header("Authorization", "Bearer LEITOR"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bibliotecarioCadastraSinopseComParagrafosELeitorPodeConsultar() throws Exception {
        String descricao = "Primeiro parágrafo.\n\nSegundo parágrafo.";
        mvc.perform(post("/api/v1/obras").header("Authorization", "Bearer BIBLIOTECARIO")
                        .contentType(MediaType.APPLICATION_JSON).content(payloadDescricao("  " + descricao + "  ")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.descricao").value(descricao));
        UUID id = obras.findAll().getFirst().getId();
        mvc.perform(get("/api/v1/obras/{id}", id).header("Authorization", "Bearer LEITOR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.descricao").value(descricao));
        mvc.perform(get("/api/v1/obras").header("Authorization", "Bearer LEITOR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].descricao").value(descricao));
    }

    @Test
    void editarDescricaoPreservaIdCapaEExemplares() throws Exception {
        Obra obra = criar("Obra com sinopse");
        obra.definirDescricao("Anterior");
        obra.definirCapa("capas/teste.jpg");
        Exemplar exemplar = exemplares.save(new Exemplar(obra, "SINOPSE-001"));
        mvc.perform(put("/api/v1/obras/{id}", obra.getId()).header("Authorization", "Bearer BIBLIOTECARIO")
                        .contentType(MediaType.APPLICATION_JSON).content(payloadDescricao("Nova sinopse")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.descricao").value("Nova sinopse"))
                .andExpect(jsonPath("$.id").value(obra.getId().toString()))
                .andExpect(jsonPath("$.capaUrl").value("/acervo/capas/" + obra.getId() + ".jpg"));
        assertThat(exemplares.findByObraId(obra.getId())).extracting(Exemplar::getId).containsExactly(exemplar.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "\n\n"})
    void descricaoVaziaRemoveSinopseAnterior(String descricao) throws Exception {
        Obra obra = criar("Com descrição");
        obra.definirDescricao("Anterior");
        mvc.perform(put("/api/v1/obras/{id}", obra.getId()).header("Authorization", "Bearer BIBLIOTECARIO")
                        .contentType(MediaType.APPLICATION_JSON).content(payloadDescricao(descricao)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.descricao").value(nullValue()));
    }

    @Test
    void cadastroAntigoSemDescricaoContinuaValido() throws Exception {
        mvc.perform(post("/api/v1/obras").header("Authorization", "Bearer BIBLIOTECARIO")
                        .contentType(MediaType.APPLICATION_JSON).content(payload("Sem sinopse", categoria.getId(), autor.getId())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.descricao").value(nullValue()));
    }

    @Test
    void rejeitaDescricaoAcimaDoLimiteNoCadastroENaEdicao() throws Exception {
        Obra obra = criar("Original");
        String body = payloadDescricao("a".repeat(5001));
        mvc.perform(post("/api/v1/obras").header("Authorization", "Bearer BIBLIOTECARIO")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("descricao"));
        mvc.perform(put("/api/v1/obras/{id}", obra.getId()).header("Authorization", "Bearer BIBLIOTECARIO")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("descricao"));
        assertThat(obra.getDescricao()).isNull();
    }

    @Test
    void aceitaDescricaoComExatamente5000Caracteres() throws Exception {
        mvc.perform(post("/api/v1/obras").header("Authorization", "Bearer BIBLIOTECARIO")
                        .contentType(MediaType.APPLICATION_JSON).content(payloadDescricao("á".repeat(5000))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.descricao").value("á".repeat(5000)));
    }

    @Test
    void leitorNaoCadastraNemAlteraDescricao() throws Exception {
        Obra obra = criar("Protegida");
        obra.definirDescricao("Original");
        mvc.perform(post("/api/v1/obras").header("Authorization", "Bearer LEITOR")
                        .contentType(MediaType.APPLICATION_JSON).content(payloadDescricao("Tentativa")))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/obras/{id}", obra.getId()).header("Authorization", "Bearer LEITOR")
                        .contentType(MediaType.APPLICATION_JSON).content(payloadDescricao("Tentativa")))
                .andExpect(status().isForbidden());
        assertThat(obra.getDescricao()).isEqualTo("Original");
    }

    private String payloadDescricao(String descricao) {
        String base = payload("Obra de teste", categoria.getId(), autor.getId()).strip();
        String texto = descricao.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
        return base.substring(0, base.length() - 1) + ",\"descricao\":\"" + texto + "\"}";
    }

    private Obra criar(String titulo) {
        return obras.saveAndFlush(new Obra(titulo, categoria, Set.of(autor), "1", "ISBN-123"));
    }

    private String payload(String titulo, UUID categoriaId, UUID autorId) {
        return """
                {"titulo":"%s","categoriaId":"%s","autorIds":["%s"]}
                """.formatted(titulo, categoriaId, autorId);
    }
}
