package com.italo.ai.memory.service;

public class MemoryTextFormatter {
    public String format(MemoryService.MemoryView memory) {
        // Older extractions stored the object as subject and a boolean as value.
        if (memory.type() == com.italo.ai.memory.model.MemoryType.PREFERENCE
                && ("true".equalsIgnoreCase(memory.value()) || "false".equalsIgnoreCase(memory.value()))) {
            boolean positive = Boolean.parseBoolean(memory.value());
            if ("DISLIKE".equalsIgnoreCase(memory.predicate()))
                return "- O usuário " + (positive ? "não gosta de " : "não declarou rejeição a ") + memory.subject() + ".";
            if ("LIKE".equalsIgnoreCase(memory.predicate()))
                return "- O usuário " + (positive ? "gosta de " : "não gosta de ") + memory.subject() + ".";
        }
        return "- [" + memory.type() + "] sujeito: " + memory.subject()
            + "; predicado: " + memory.predicate() + "; valor: " + memory.value();
    }
}
