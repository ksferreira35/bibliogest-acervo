package br.com.bibliogest.acervo_api.entities;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
public class Exemplar {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "obra_id")
    private Obra obra;

    @Column(name = "codigo_tombo", nullable = false, unique = true, length = 100)
    private String codigoTombo;

    protected Exemplar() {}

    public Exemplar(Obra obra, String codigoTombo) {
        this.obra = obra;
        this.codigoTombo = codigoTombo;
    }

    public UUID getId() {
        return id;
    }

    public Obra getObra() {
        return obra;
    }

    public String getCodigoTombo() {
        return codigoTombo;
    }
}
