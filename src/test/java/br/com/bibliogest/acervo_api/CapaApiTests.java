package br.com.bibliogest.acervo_api;

import br.com.bibliogest.acervo_api.entities.Categoria;
import br.com.bibliogest.acervo_api.entities.Obra;
import br.com.bibliogest.acervo_api.repository.CategoriaRepository;
import br.com.bibliogest.acervo_api.repository.ObraRepository;
import br.com.bibliogest.acervo_api.service.CapaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:capas-tests;MODE=PostgreSQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://login.example.test/realms/bibliogest",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://login.example.test/certs"
})
@AutoConfigureMockMvc
class CapaApiTests {
    private static final String CONTEXTO = "/acervo";
    private static final String OBRAS = CONTEXTO + "/api/v1/obras";

    @TempDir static Path armazenamento;
    @Autowired MockMvc mvc;
    @Autowired ObraRepository obras;
    @Autowired CategoriaRepository categorias;
    @Autowired CapaService capas;
    @Autowired TransactionTemplate transacao;
    @MockitoBean JwtDecoder decoder;

    @DynamicPropertySource
    static void configurarPasta(DynamicPropertyRegistry registry) {
        registry.add("bibliogest.capas.diretorio", () -> armazenamento.toString());
    }

    @BeforeEach
    void prepararTokens() {
        when(decoder.decode("leitor")).thenReturn(token("leitor", "LEITOR"));
        when(decoder.decode("bibliotecario")).thenReturn(token("bibliotecario", "BIBLIOTECARIO"));
    }

    @Test
    void bibliotecarioEnviaCapaELeitorRecebeUrlAoConsultarObra() throws Exception {
        UUID id = criarObra();
        String url = url(id);

        enviar(id, imagem(24, "png"), "bibliotecario")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capaUrl").value(url));

        mvc.perform(get(OBRAS + "/" + id).contextPath(CONTEXTO)
                        .header("Authorization", "Bearer leitor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capaUrl").value(url));

        assertThat(lerCapa(id).getWidth()).isEqualTo(24);
        assertThat(arquivoAtual(id)).exists();
        assertThat(chaveAtual(id)).startsWith("capas/").endsWith(".jpg");
    }

    @Test
    void bibliotecarioSubstituiCapaMantendoAUrl() throws Exception {
        UUID id = criarObra();
        enviar(id, imagem(24, "png"), "bibliotecario").andExpect(status().isOk());
        Path anterior = arquivoAtual(id);

        enviar(id, imagem(48, "jpeg"), "bibliotecario")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capaUrl").value(url(id)));

        assertThat(lerCapa(id).getWidth()).isEqualTo(48);
        assertThat(anterior).doesNotExist();
    }

    @Test
    void rollbackPreservaAImagemAnterior() throws Exception {
        UUID id = criarObra();
        enviar(id, imagem(24, "png"), "bibliotecario").andExpect(status().isOk());
        Path anterior = arquivoAtual(id);
        MockMultipartFile substituta = imagem(48, "png");

        Path descartada = transacao.execute(status -> {
            capas.salvar(id, substituta);
            Path nova = arquivoAtual(id);
            status.setRollbackOnly();
            return nova;
        });

        assertThat(descartada).doesNotExist();
        assertThat(anterior).exists();
        assertThat(lerCapa(id).getWidth()).isEqualTo(24);
    }

    @Test
    void leitorNaoPodeEnviarCapa() throws Exception {
        UUID id = criarObra();

        enviar(id, imagem(24, "png"), "leitor").andExpect(status().isForbidden());

        assertThat(chaveAtual(id)).isNull();
    }

    @Test
    void rejeitaArquivoQueNaoEImagemMesmoComTipoDeclaradoComoPng() throws Exception {
        UUID id = criarObra();
        enviar(id, imagem(24, "png"), "bibliotecario").andExpect(status().isOk());
        Path anterior = arquivoAtual(id);
        MockMultipartFile invalido = new MockMultipartFile(
                "arquivo", "capa.png", "image/png", new byte[] {1, 2, 3}
        );

        enviar(id, invalido, "bibliotecario").andExpect(status().isUnsupportedMediaType());

        assertThat(arquivoAtual(id)).isEqualTo(anterior);
        assertThat(lerCapa(id).getWidth()).isEqualTo(24);
    }

    @Test
    void obraSemCapaRetornaUrlNulaEImagem404() throws Exception {
        UUID id = criarObra();

        mvc.perform(get(OBRAS + "/" + id).contextPath(CONTEXTO)
                        .header("Authorization", "Bearer leitor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capaUrl").value(nullValue()));

        mvc.perform(get(url(id)).contextPath(CONTEXTO)).andExpect(status().isNotFound());
    }

    @Test
    void excluirObraSemExemplaresRemoveCapaSomenteDepoisDoCommit() throws Exception {
        UUID id = criarObra();
        enviar(id, imagem(24, "png"), "bibliotecario").andExpect(status().isOk());
        Path arquivo = arquivoAtual(id);
        mvc.perform(delete(OBRAS + "/" + id).contextPath(CONTEXTO)
                        .header("Authorization", "Bearer bibliotecario"))
                .andExpect(status().isNoContent());
        assertThat(arquivo).doesNotExist();
        mvc.perform(get(url(id)).contextPath(CONTEXTO)).andExpect(status().isNotFound());
    }

    @Test
    void rollbackDaExclusaoPreservaCadastroECapa() throws Exception {
        UUID id = criarObra();
        enviar(id, imagem(24, "png"), "bibliotecario").andExpect(status().isOk());
        Path arquivo = arquivoAtual(id);
        transacao.executeWithoutResult(status -> {
            try {
                mvc.perform(delete(OBRAS + "/" + id).contextPath(CONTEXTO)
                                .header("Authorization", "Bearer bibliotecario"))
                        .andExpect(status().isNoContent());
            } catch (Exception erro) {
                throw new IllegalStateException(erro);
            }
            status.setRollbackOnly();
        });
        assertThat(obras.existsById(id)).isTrue();
        assertThat(arquivo).exists();
        assertThat(lerCapa(id).getWidth()).isEqualTo(24);
    }

    private UUID criarObra() {
        Categoria categoria = categorias.save(new Categoria("Mangá"));
        return obras.save(new Obra("Obra de teste", categoria, new HashSet<>(), null, null)).getId();
    }

    private MockMultipartFile imagem(int largura, String formato) throws IOException {
        BufferedImage imagem = new BufferedImage(largura, 48, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(imagem, formato, bytes);
        return new MockMultipartFile("arquivo", "capa." + formato, "image/" + formato, bytes.toByteArray());
    }

    private ResultActions enviar(UUID id, MockMultipartFile imagem, String token)
            throws Exception {
        return mvc.perform(multipart(HttpMethod.PUT, OBRAS + "/{obraId}/capa", id)
                .file(imagem)
                .contextPath(CONTEXTO)
                .header("Authorization", "Bearer " + token));
    }

    private BufferedImage lerCapa(UUID id) throws Exception {
        byte[] bytes = mvc.perform(get(url(id)).contextPath(CONTEXTO))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsByteArray();
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    private String chaveAtual(UUID id) {
        return obras.findById(id).orElseThrow().getCapaChave();
    }

    private Path arquivoAtual(UUID id) {
        return armazenamento.resolve(chaveAtual(id));
    }

    private String url(UUID id) {
        return CONTEXTO + "/capas/" + id + ".jpg";
    }

    private Jwt token(String valor, String role) {
        return Jwt.withTokenValue(valor)
                .header("alg", "RS256")
                .subject(valor)
                .claim("realm_access", Map.of("roles", List.of(role)))
                .build();
    }
}
