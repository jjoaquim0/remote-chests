package dev.joaquim.remotechests.model;

import java.util.Map;

/**
 * Erro de regra de negocio que vira mensagem para o jogador.
 * O {@code messageKey} referencia uma chave da secao "messages" do config.yml.
 */
public final class ChestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String messageKey;
    private final transient Map<String, String> placeholders;

    public ChestException(String messageKey, Map<String, String> placeholders) {
        super(messageKey, null, false, false);
        this.messageKey = messageKey;
        this.placeholders = placeholders;
    }

    public ChestException(String messageKey) {
        this(messageKey, Map.of());
    }

    public String messageKey() {
        return messageKey;
    }

    public Map<String, String> placeholders() {
        return placeholders;
    }
}
