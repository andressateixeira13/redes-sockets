package br.ufsm.politecnico.csi.redes.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;

public class Mensagem implements Serializable {
    private String tipoMensagem;
    private String remetente;
    private String conteudo;
    private String status;

    public Mensagem() {
        // Construtor vazio
    }

    public Mensagem(String tipoMensagem, String remetente, String status, String conteudo ) {
        this.tipoMensagem = tipoMensagem;
        this.remetente = remetente;
        this.status = status;
        this.conteudo = conteudo;
    }

    public String getTipoMensagem() {
        return tipoMensagem;
    }

    public void setTipoMensagem(String tipoMensagem) {
        this.tipoMensagem = tipoMensagem;
    }

    public String getRemetente() {
        return remetente;
    }

    public void setRemetente(String remetente) {
        this.remetente = remetente;
    }

    public String getConteudo() {
        return conteudo;
    }

    public void setConteudo(String conteudo) {
        this.conteudo = conteudo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
