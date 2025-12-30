package com.whatsappanalytics.whatsapp_analytics_backend.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.web.multipart.MultipartFile;

/**
 * Security validator to prevent code execution and malicious file uploads.
 */
public class SecurityValidator {

    // Dangerous file extensions that could execute code
    private static final Set<String> DANGEROUS_EXTENSIONS = new HashSet<>(Arrays.asList(
        // Executables
        "exe", "bat", "cmd", "com", "scr", "vbs", "wsf", "msi", "msh",
        // Scripts
        "sh", "bash", "zsh", "ps1", "psm1", "psd1", "py", "pyc", "pyw", "pyo",
        "rb", "pl", "pm", "php", "php3", "php4", "php5", "phtml", "jsp", "jspx",
        "asp", "aspx", "js", "jar", "war", "ear",
        // System files
        "dll", "so", "dylib", "sys", "drv", "ocx", "cpl",
        // Archives that could contain executables
        "app", "deb", "rpm", "pkg", "dmg", "iso",
        // Other dangerous
        "lnk", "url", "reg", "inf", "ini"
    ));

    // Dangerous filename patterns
    private static final Set<String> DANGEROUS_PATTERNS = new HashSet<>(Arrays.asList(
        "..", "/", "\\", ":", "*", "?", "\"", "<", ">", "|"
    ));

    /**
     * Sanitize text input to prevent injection attacks
     * @param text The text to sanitize
     * @param maxLength Maximum allowed length
     * @return Sanitized text
     */
    public static String sanitizeText(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        
        // Limit length
        if (text.length() > maxLength) {
            text = text.substring(0, maxLength);
        }
        
        // Remove control characters (keep newlines and tabs)
        StringBuilder sanitized = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c >= 32 || c == '\n' || c == '\t') {
                sanitized.append(c);
            }
        }
        
        return sanitized.toString().trim();
    }

    /**
     * Validate filename for path traversal and dangerous characters
     */
    public static boolean isValidFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }

        // Check for path traversal
        String normalized = filename.replace("\\", "/");
        if (normalized.contains("../") || normalized.contains("..\\") || 
            normalized.startsWith("/") || normalized.startsWith("\\")) {
            return false;
        }

        // Check for dangerous characters
        for (String pattern : DANGEROUS_PATTERNS) {
            if (filename.contains(pattern)) {
                return false;
            }
        }

        // Check for dangerous extensions
        String lowerFilename = filename.toLowerCase();
        for (String ext : DANGEROUS_EXTENSIONS) {
            if (lowerFilename.endsWith("." + ext)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Validate ZIP file contents for dangerous files
     */
    public static String validateZipContents(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream();
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            
            ZipEntry entry;
            boolean hasChatFile = false;
            int entryCount = 0;
            final int MAX_ENTRIES = 1000; // Prevent zip bombs
            
            while ((entry = zipInputStream.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ENTRIES) {
                    return "ZIP file contains too many entries (possible zip bomb)";
                }

                String entryName = entry.getName();
                
                // Validate entry name for path traversal
                if (!isValidFilename(entryName)) {
                    return "ZIP contains invalid or dangerous filename: " + entryName;
                }

                // Check for dangerous file types
                String lowerEntryName = entryName.toLowerCase();
                for (String ext : DANGEROUS_EXTENSIONS) {
                    if (lowerEntryName.endsWith("." + ext)) {
                        return "ZIP contains dangerous file type: " + entryName;
                    }
                }

                // Check for _chat.txt file
                if (lowerEntryName.endsWith("_chat.txt")) {
                    hasChatFile = true;
                }

                zipInputStream.closeEntry();
            }
            
            if (!hasChatFile) {
                return "The ZIP file does not contain a .txt file with the chat";
            }
            
            return null; // Valid
            
        } catch (IOException e) {
            return "Error reading ZIP file: " + e.getMessage();
        }
    }

    /**
     * Validate uploaded file for security
     */
    public static String validateFile(MultipartFile file) {
        // Check if file is empty
        if (file.isEmpty()) {
            return "File is empty";
        }
        
        // Check file size (5MB = 5 * 1024 * 1024 bytes)
        long maxSize = 5 * 1024 * 1024; // 5MB
        long fileSize = file.getSize();
        if (fileSize > maxSize) {
            return "File size exceeds 5MB limit";
        }
        
        String filename = file.getOriginalFilename();
        if (filename == null) {
            return "Invalid filename";
        }
        
        // Validate filename
        if (!isValidFilename(filename)) {
            return "Invalid or dangerous filename";
        }
        
        String lowerFilename = filename.toLowerCase();
        
        // Accept .txt files directly
        if (lowerFilename.endsWith(".txt")) {
            return null; // TXT file is valid
        }
        
        // For ZIP files, validate contents
        if (lowerFilename.endsWith(".zip")) {
            return validateZipContents(file);
        }
        
        // File type not supported
        return "File must be a ZIP file or a TXT file";
    }
}

