package com.italo.ai.tools;

import com.italo.ai.search.BraveSearchException;
import com.italo.ai.search.BraveSearchService;
import com.italo.ai.search.SearchResult;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class WebSearchTool {
    private final BraveSearchService search;

    public WebSearchTool(BraveSearchService search) {
        this.search = search;
    }

    @Tool(description = """
        Pesquisa informações públicas na internet usando Brave Search.
        Use para pesquisas solicitadas pelo usuário, informações atuais e identificação
        ou biografia de pessoas. Retorna até cinco títulos, URLs e trechos de fontes.
        Não abre páginas. Se error estiver preenchido, a pesquisa falhou;
        resultados vazios sem erro significam que não foram encontradas fontes.
        """)
    public SearchResponse searchWeb(
            @ToolParam(description = "Termo de pesquisa, com o nome ou assunto e contexto público relevante.") String query) {
        try {
            return new SearchResponse(search.search(query), null);
        } catch (BraveSearchException failure) {
            return new SearchResponse(List.of(), failure.reason().name() + ": " + failure.getMessage());
        } catch (IllegalArgumentException failure) {
            return new SearchResponse(List.of(), "INVALID_QUERY: informe um termo de pesquisa não vazio.");
        }
    }

    public record SearchResponse(List<SearchResult> results, String error) {}
}
