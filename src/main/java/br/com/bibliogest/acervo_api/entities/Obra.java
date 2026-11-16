package br.com.bibliogest.acervo_api.entities;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
public class Obra {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String titulo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @ManyToMany
    @JoinTable(
            name = "obra_autor",
            joinColumns = @JoinColumn(name = "obra_id"),
            inverseJoinColumns = @JoinColumn(name = "autor_id")
    )
    private Set<Autor> autores = new HashSet<>();

    @Column(length = 50)
    private String volume;

    @Column(length = 30)
    private String isbn;

    @Column(name = "capa_chave", length = 120)
    private String capaChave;

    protected Obra() {}

    public Obra(String titulo, Categoria categoria, Set<Autor> autores, String volume, String isbn) {
        this.titulo = titulo;
        this.categoria = categoria;
        this.autores = autores;
        this.volume = volume;
        this.isbn = isbn;
    }

    public UUID getId() {
        return id;
    }

    public String getTitulo() {
        return titulo;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    public Set<Autor> getAutores() {
        return autores;
    }

    public String getVolume() {
        return volume;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getCapaChave() {
        return capaChave;
    }

    public void definirCapa(String chave) {
        this.capaChave = chave;
    }
}
