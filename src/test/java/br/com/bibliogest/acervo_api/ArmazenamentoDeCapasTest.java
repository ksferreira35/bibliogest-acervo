package br.com.bibliogest.acervo_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.bibliogest.acervo_api.service.ArmazenamentoDeCapas;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.server.ResponseStatusException;

class ArmazenamentoDeCapasTest {
    @TempDir Path root;

    private ArmazenamentoDeCapas storage(long limit) {
        return new ArmazenamentoDeCapas(root.toString(), DataSize.ofBytes(limit));
    }

    @Test
    void rejectsEmptyOversizedAndUnsupportedFiles() throws IOException {
        var storage = storage(100_000);
        assertThatThrownBy(() -> storage.salvar(file(new byte[0])))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        assertThatThrownBy(() -> storage(1).salvar(file(new byte[2])))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("413");
        assertThatThrownBy(() -> storage.salvar(file(image(2, 2, "gif"))))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("415");
        assertThatThrownBy(() -> storage.salvar(file(new byte[]{1, 2, 3})))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("415");
    }

    @Test
    void validatesBothImageDimensions() {
        assertThatThrownBy(() -> storage(100_000).salvar(file(image(2049, 1, "png"))))
                .hasMessageContaining("2048");
        assertThatThrownBy(() -> storage(100_000).salvar(file(image(1, 2049, "png"))))
                .hasMessageContaining("2048");
    }

    @Test
    void convertsToJpegAndRemovesOnlyInsideStorage() throws IOException {
        var storage = storage(100_000);
        String key = storage.salvar(file(image(2, 3, "png")));
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(storage.carregar(key))).getHeight()).isEqualTo(3);
        storage.remover(key);
        storage.remover(null);
        assertThat(Files.exists(root.resolve(key))).isFalse();
        assertThatThrownBy(() -> storage.carregar(key)).hasMessageContaining("404");
        assertThatThrownBy(() -> storage.carregar("../outside.jpg")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> storage.remover("../outside.jpg")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reportsFailureWhenStorageDirectoryCannotBeCreated() throws IOException {
        Files.writeString(root.resolve("capas"), "not a directory");
        assertThatThrownBy(() -> storage(100_000).salvar(file(image(2, 2, "png"))))
                .hasMessageContaining("500");
    }

    private MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("arquivo", "cover.png", "image/png", bytes);
    }

    private byte[] image(int width, int height, String format) throws IOException {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, output);
        return output.toByteArray();
    }
}
