package br.com.bibliogest.acervo_api.entities;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
public class Categoria {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String nome;

    protected Categoria() {}

    public Categoria(String nome) {
        this.nome = nome;
    }

    public UUID getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }
}
