package com.contentgrid.pathfinder.config;

import io.fabric8.kubernetes.client.Config;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
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
    public static class PathfinderTargetProperties {
        private String namespace;
        private Map<String, String> annotations = new HashMap<>();
        private List<ServiceMappingProperties> services = new ArrayList<>();
    }

    @Data
    public static class ServiceMappingProperties {
        private String path;
        private String pathType;
        private String serviceName;
        private int servicePort;
        private String servicePortName;
    }
}
