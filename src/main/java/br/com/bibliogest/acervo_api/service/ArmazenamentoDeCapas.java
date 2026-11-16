package br.com.bibliogest.acervo_api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

@Component
public class ArmazenamentoDeCapas {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArmazenamentoDeCapas.class);
    private static final int DIMENSAO_MAXIMA = 2048;
    private static final Set<String> FORMATOS = Set.of("jpeg", "png");

    private final Path raiz;
    private final Path pastaCapas;
    private final long limiteEmBytes;

    public ArmazenamentoDeCapas(
            @Value("${bibliogest.capas.diretorio}") String diretorio,
            @Value("${spring.servlet.multipart.max-file-size}") DataSize limite
    ) {
        this.raiz = Path.of(diretorio).toAbsolutePath().normalize();
        this.pastaCapas = raiz.resolve("capas");
        this.limiteEmBytes = limite.toBytes();
    }

    public String salvar(MultipartFile arquivo) {
        BufferedImage imagem = lerImagem(arquivo);
        Path destino = null;

        try {
            Files.createDirectories(pastaCapas);
            destino = Files.createTempFile(pastaCapas, "capa-", ".jpg");
            try (var saida = new FileImageOutputStream(destino.toFile())) {
                if (!ImageIO.write(emRgb(imagem), "jpeg", saida)) {
                    throw new IOException("Codificador JPEG indisponível");
                }
            }
            return "capas/" + destino.getFileName();
        } catch (IOException | RuntimeException erro) {
            if (destino != null) {
                apagar(destino);
            }
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível salvar a capa", erro
            );
        }
    }

    public byte[] carregar(String chave) {
        try {
            return Files.readAllBytes(caminho(chave));
        } catch (NoSuchFileException erro) {
            LOGGER.warn("Arquivo de uma capa cadastrada está ausente: {}", chave);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Capa não encontrada");
        } catch (IOException erro) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível ler a capa", erro
            );
        }
    }

    public void remover(String chave) {
        if (chave != null) {
            apagar(caminho(chave));
        }
    }

    private Path caminho(String chave) {
        Path arquivo = raiz.resolve(chave).normalize();
        if (!pastaCapas.equals(arquivo.getParent())) {
            throw new IllegalStateException("Chave de capa inválida");
        }
        return arquivo;
    }

    private void apagar(Path arquivo) {
        try {
            Files.deleteIfExists(arquivo);
        } catch (IOException erro) {
            LOGGER.warn("Não foi possível apagar o arquivo da capa {}", arquivo.getFileName(), erro);
        }
    }

    private BufferedImage lerImagem(MultipartFile arquivo) {
        if (arquivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie uma imagem para a capa");
        }
        if (arquivo.getSize() > limiteEmBytes) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "A capa excede o tamanho máximo");
        }

        try (InputStream entrada = arquivo.getInputStream();
             var dados = new MemoryCacheImageInputStream(entrada)) {
            Iterator<ImageReader> leitores = ImageIO.getImageReaders(dados);
            if (!leitores.hasNext()) {
                throw formatoInvalido(null);
            }

            ImageReader leitor = leitores.next();
            try {
                if (!FORMATOS.contains(leitor.getFormatName().toLowerCase(Locale.ROOT))) {
                    throw formatoInvalido(null);
                }
                leitor.setInput(dados, true, true);
                if (leitor.getWidth(0) > DIMENSAO_MAXIMA || leitor.getHeight(0) > DIMENSAO_MAXIMA) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "A imagem deve ter no máximo 2048 × 2048 pixels"
                    );
                }
                return leitor.read(0);
            } finally {
                leitor.dispose();
            }
        } catch (IOException | IllegalArgumentException erro) {
            throw formatoInvalido(erro);
        }
    }

    private ResponseStatusException formatoInvalido(Exception erro) {
        return new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Envie uma imagem JPEG ou PNG válida", erro);
    }

    private BufferedImage emRgb(BufferedImage imagem) {
        BufferedImage rgb = new BufferedImage(imagem.getWidth(), imagem.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D desenho = rgb.createGraphics();
        try {
            desenho.drawImage(imagem, 0, 0, Color.WHITE, null);
        } finally {
            desenho.dispose();
        }
        return rgb;
    }
}
