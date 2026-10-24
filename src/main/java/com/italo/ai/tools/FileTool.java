package com.italo.ai.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Component
public class FileTool {

    private static final Logger log =
            LoggerFactory.getLogger(FileTool.class);

    private final Path directory;

    public FileTool(@Value("${app.files.directory:./output}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    @Tool(description = """
        Salva conteúdo de texto em UTF-8 em um novo arquivo na pasta de saída configurada.
        Use quando o usuário solicitar salvar conteúdo em arquivo.
        Aceita somente o nome do arquivo, sem pastas. Não sobrescreve arquivos existentes.
        Só confirme a gravação se success for true; caso contrário, informe o erro retornado.
        """)
    public SaveFileResult saveFile(
            @ToolParam(description = "Nome do arquivo com extensão, sem caminhos ou pastas, como resumo.md.") String filename,
            @ToolParam(description = "Conteúdo completo do arquivo de texto.") String content) {

        log.info("Tool saveFile chamada");
        log.info("Filename: {}", filename);
        log.info("Content:\n{}", content);
        if (filename == null || filename.isBlank() || content == null) {
            return new SaveFileResult(false, null, "INVALID_INPUT: informe nome e conteúdo do arquivo.");
        }
        try {
            Path name = Path.of(filename);
            if (name.isAbsolute() || name.getNameCount() != 1
                    || filename.contains("/") || filename.contains("\\")
                    || filename.equals(".") || filename.equals("..")) {
                return new SaveFileResult(false, null, "INVALID_FILENAME: informe somente o nome do arquivo, sem pastas.");
            }
            Files.createDirectories(directory);
            Path target = directory.toRealPath().resolve(name);
            Files.writeString(target, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return new SaveFileResult(true, target.toString(), null);
        } catch (FileAlreadyExistsException failure) {
            return new SaveFileResult(false, null, "FILE_ALREADY_EXISTS: escolha outro nome de arquivo.");
        } catch (InvalidPathException failure) {
            return new SaveFileResult(false, null, "INVALID_FILENAME: nome de arquivo inválido.");
        } catch (IOException | SecurityException failure) {
            return new SaveFileResult(false, null, "WRITE_FAILED: não foi possível salvar o arquivo.");
        }
    }

    public record SaveFileResult(boolean success, String path, String error) {}
}
