package com.contentgrid.pathfinder.config;

import io.fabric8.kubernetes.client.Config;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pathfinder", ignoreUnknownFields = false)
@Data
@Slf4j
public class PathfinderProperties {
    private Config kubernetes;
    private PathfinderSourceProperties source = new PathfinderSourceProperties();
    private PathfinderTargetProperties target = new PathfinderTargetProperties();

    @Data
    public static class PathfinderSourceProperties {
        private String namespace;
        private Map<String, String> labels = new HashMap<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PathfinderTargetProperties {

        private String namespace;
        private String ingressClassName;
        @Singular
        private Map<String, String> annotations = new HashMap<>();
        @Singular
        private Map<String, AnnotationCopySpec> copyAnnotations = new HashMap<>();
        @Singular
        private List<ServiceMappingProperties> services = new ArrayList<>();
        @Builder.Default
        private PathfinderTlsProperties tls = new PathfinderTlsProperties();
    }

    @Data
    public static class ServiceMappingProperties {
        private String path;
        private String pathType;
        private String serviceName;
        private int servicePort;
        private String servicePortName;
    }

    @Data
    public static class PathfinderTlsProperties {

        private String fallbackCnHostname;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnnotationCopySpec {

        private String defaultValue;
        @Builder.Default
        private Set<String> acceptableValues = new HashSet<>();

        public Set<String> getAcceptableValues() {
            if (acceptableValues.isEmpty()) {
                return Set.of();
            }
            if (!acceptableValues.contains(defaultValue)) {
                var copy = new HashSet<>(acceptableValues);
                copy.add(defaultValue);
                return Set.copyOf(copy);
            }
            return Set.copyOf(acceptableValues);
        }
    }
}
