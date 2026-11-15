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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:acervo-tests;MODE=PostgreSQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://login.example.test/realms/bibliogest",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://login.example.test/certs"
})
@AutoConfigureMockMvc
class ExemplarApiTests {

    private static final String EXEMPLARES = "/api/v1/obras/{obraId}/exemplares";

    @Autowired MockMvc mvc;
    @Autowired AutorRepository autorRepository;
    @Autowired CategoriaRepository categoriaRepository;
    @Autowired ObraRepository obraRepository;
    @Autowired ExemplarRepository exemplarRepository;
    @MockitoBean JwtDecoder decoder;

    @BeforeEach
    void prepararTokens() {
        when(decoder.decode("leitor")).thenReturn(token("leitor", "LEITOR"));
        when(decoder.decode("bibliotecario")).thenReturn(token("bibliotecario", "BIBLIOTECARIO"));
    }

    @Test
    void leitorListaSomenteExemplaresDaObraSolicitada() throws Exception {
        Obra tokyoGhoul = criarObra("Tokyo Ghoul");
        Obra berserk = criarObra("Berserk");
        exemplarRepository.save(new Exemplar(tokyoGhoul, "FILTRO-TG-01"));
        exemplarRepository.save(new Exemplar(tokyoGhoul, "FILTRO-TG-02"));
        exemplarRepository.save(new Exemplar(berserk, "FILTRO-BK-01"));

        mvc.perform(get(EXEMPLARES, tokyoGhoul.getId())
                        .header("Authorization", "Bearer leitor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].codigoTombo",
                        containsInAnyOrder("FILTRO-TG-01", "FILTRO-TG-02")))
                .andExpect(jsonPath("$[0].obraId").value(tokyoGhoul.getId().toString()))
                .andExpect(jsonPath("$[1].obraId").value(tokyoGhoul.getId().toString()));
    }

    @Test
    void leitorRecebeListaVaziaQuandoObraNaoTemExemplares() throws Exception {
        Obra obra = criarObra("Obra sem cópias");

        mvc.perform(get(EXEMPLARES, obra.getId())
                        .header("Authorization", "Bearer leitor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void bibliotecarioCadastraExemplarELeitorConsultaOCadastro() throws Exception {
        Obra obra = criarObra("Obra para cadastro");

        mvc.perform(post(EXEMPLARES, obra.getId())
                        .header("Authorization", "Bearer bibliotecario")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigoTombo":"CADASTRO-01"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.obraId").value(obra.getId().toString()))
                .andExpect(jsonPath("$.codigoTombo").value("CADASTRO-01"));

        mvc.perform(get(EXEMPLARES, obra.getId())
                        .header("Authorization", "Bearer leitor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].codigoTombo", containsInAnyOrder("CADASTRO-01")));
    }

    @Test
    void leitorNaoPodeCadastrarExemplar() throws Exception {
        Obra obra = criarObra("Obra protegida");

        mvc.perform(post(EXEMPLARES, obra.getId())
                        .header("Authorization", "Bearer leitor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigoTombo":"PROTEGIDO-01"}
                                """))
                .andExpect(status().isForbidden());
    }

    private Obra criarObra(String titulo) {
        Categoria categoria = categoriaRepository.save(new Categoria("Mangá"));
        Autor autor = autorRepository.save(new Autor("Autor de teste"));
        return obraRepository.save(new Obra(titulo, categoria, Set.of(autor), "1", null));
    }

    private Jwt token(String valor, String role) {
        return Jwt.withTokenValue(valor)
                .header("alg", "RS256")
                .subject(valor)
                .claim("realm_access", Map.of("roles", List.of(role)))
                .build();
    }
}
