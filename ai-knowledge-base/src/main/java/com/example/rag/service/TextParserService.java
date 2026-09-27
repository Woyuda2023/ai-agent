package com.example.rag.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文档内容解析：PDF / TXT。
 */
@Service
public class TextParserService {

    public String parse(String filePath, String fileType) throws IOException {
        if ("pdf".equalsIgnoreCase(fileType)) {
            return parsePdf(filePath);
        }
        if ("txt".equalsIgnoreCase(fileType)) {
            return parseTxt(filePath);
        }
        throw new IllegalArgumentException("不支持的文件类型: " + fileType);
    }

    private String parsePdf(String path) throws IOException {
        try (PDDocument document = Loader.loadPDF(new File(path))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    /**
     * TXT 解析：优先按 UTF-8 解码；若出现替换字符（乱码）则回退 GBK。
     */
    private String parseTxt(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Path.of(path));
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.indexOf('\uFFFD') >= 0 && bytes.length > 0) {
            text = new String(bytes, Charset.forName("GBK"));
        }
        return text;
    }
}
