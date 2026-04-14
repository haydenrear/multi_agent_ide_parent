package com.hayden.multiagentide.prompt;

import com.hayden.multiagentide.artifact.PromptTemplateVersion;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Loads prompt templates from configured resource paths at application startup.
 * 
 * Templates are loaded from:
 * - classpath:prompts/workflow/ (built-in templates)
 * - Additional paths configured via artifacts.templates.source-paths
 * 
 * Each template file is assigned a static ID based on its path:
 * - prompts/workflow/discovery.txt -> tpl.workflow.discovery
 * - prompts/workflow/planning/orchestrator.txt -> tpl.workflow.planning.orchestrator
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PromptTemplateLoader {
    
    private final PromptTemplateStore templateStore;
    
    @Value("${artifacts.templates.load-on-startup:true}")
    private boolean loadOnStartup;
    
    @Value("${artifacts.templates.source-paths:classpath:prompts/workflow/}")
    private List<String> sourcePaths;
    
    private final PathMatchingResourcePatternResolver resourceResolver = 
            new PathMatchingResourcePatternResolver();
    
    @PostConstruct
    public void loadTemplates() {
        if (!loadOnStartup) {
            log.info("Template loading on startup is disabled");
            return;
        }
        
        log.info("Loading prompt templates from {} source paths", sourcePaths.size());
        int loaded = 0;
        int created = 0;
        
        for (String sourcePath : sourcePaths) {
            try {
                List<PromptTemplateVersion> templates = loadFromPath(sourcePath);
                for (PromptTemplateVersion template : templates) {
                    Optional<PromptTemplateVersion> existing = templateStore.registerTemplate(template);
                    if (existing.isEmpty()) {
                        created++;
                    }
                    loaded++;
                }
            } catch (Exception e) {
                log.error("Failed to load templates from {}: {}", sourcePath, e.getMessage());
            }
        }
        
        log.info("Loaded {} templates ({} new versions created)", loaded, created);
    }
    
    /**
     * Loads all templates from a resource path.
     */
    public List<PromptTemplateVersion> loadFromPath(String basePath) throws IOException {
        List<PromptTemplateVersion> templates = new ArrayList<>();
        
        // Normalize path
        String pattern = basePath;
        if (!pattern.endsWith("/")) {
            pattern += "/";
        }
        pattern += "**/*";
        
        // Find all resources
        Resource[] resources = resourceResolver.getResources(pattern);
        
        for (Resource resource : resources) {
            if (!resource.isReadable() || resource.getFilename() == null) {
                continue;
            }
            
            String filename = resource.getFilename();
            if (!isTemplateFile(filename)) {
                continue;
            }
            
            try {
                PromptTemplateVersion template = loadTemplate(resource, basePath);
                if (template != null) {
                    templates.add(template);
                }
            } catch (Exception e) {
                log.warn("Failed to load template {}: {}", resource.getFilename(), e.getMessage());
            }
        }
        
        return templates;
    }
    
    /**
     * Loads a single template from a resource.
     */
    public PromptTemplateVersion loadTemplate(Resource resource, String basePath) throws IOException {
        String templateText;
        try (InputStream is = resource.getInputStream()) {
            templateText = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        
        // Derive static ID from resource path
        String staticId = deriveStaticId(resource, basePath);
        if (staticId == null) {
            log.warn("Could not derive static ID for resource: {}", resource.getFilename());
            return null;
        }
        
        // Validate static ID format
        if (!PromptTemplateVersion.isValidStaticId(staticId)) {
            log.warn("Invalid static ID format: {} (from {})", staticId, resource.getFilename());
            return null;
        }
        
        String sourceLocation = resource.getURI().toString();
        
        return PromptTemplateVersion.create(staticId, templateText, sourceLocation);
    }
    
    /**
     * Reloads all templates from configured paths.
     */
    public void reloadAll() {
        log.info("Reloading all templates");
        loadTemplates();
    }
    
    // ========== Private Helpers ==========
    
    private boolean isTemplateFile(String filename) {
        if (filename == null) {
            return false;
        }
        String lower = filename.toLowerCase();
        return lower.endsWith(".txt") 
                || lower.endsWith(".j2")
                || lower.endsWith(".jinja2")
                || lower.endsWith(".prompt");
    }
    
    private String deriveStaticId(Resource resource, String basePath) {
        try {
            String uri = resource.getURI().toString();
            
            // Find the base path marker
            String normalizedBase = basePath.replace("classpath:", "").replace("classpath*:", "");
            int baseIndex = uri.indexOf(normalizedBase);
            
            String relativePath;
            if (baseIndex >= 0) {
                relativePath = uri.substring(baseIndex + normalizedBase.length());
            } else {
                // Fallback to filename
                relativePath = resource.getFilename();
            }
            
            if (relativePath == null) {
                return null;
            }
            
            // Remove leading slash
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }
            
            // Remove file extension
            int dotIndex = relativePath.lastIndexOf('.');
            if (dotIndex > 0) {
                relativePath = relativePath.substring(0, dotIndex);
            }
            
            // Convert path separators to dots and normalize
            String staticId = "tpl." + relativePath
                    .replace('/', '.')
                    .replace('\\', '.')
                    .replace('-', '_')
                    .toLowerCase();
            
            // Remove duplicate dots
            while (staticId.contains("..")) {
                staticId = staticId.replace("..", ".");
            }
            
            return staticId;
        } catch (Exception e) {
            log.warn("Failed to derive static ID: {}", e.getMessage());
            return null;
        }
    }
}
