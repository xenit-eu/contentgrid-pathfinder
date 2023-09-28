package com.contentgrid.pathfinder.kcontroller;

import com.contentgrid.pathfinder.config.PathfinderProperties.AnnotationCopySpec;
import com.contentgrid.pathfinder.config.PathfinderProperties.PathfinderTargetProperties;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class ConfigMapManager {

    private final PathfinderTargetProperties targetProperties;

    public static final String PATHFINDER_FINALIZER = "pathfinder.contentgrid.com/ingress";

    private Stream<AnnotationHandler> handlers() {
        return targetProperties.getCopyAnnotations().entrySet()
                .stream()
                .map(entry -> new AnnotationHandler(entry.getKey(), entry.getValue()));
    }

    public Map<String, String> getIngressAnnotations(ConfigMap configMap) {
        return Stream.concat(
                        targetProperties.getAnnotations().entrySet().stream(),
                        handlers()
                                .map(handler -> handler.readValue(configMap.getMetadata()))
                )
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    }

    public Optional<UnaryOperator<ConfigMap>> linkToIngress(ConfigMap configMap, Ingress ingressConfig) {
        if (!configMap.hasFinalizer(PATHFINDER_FINALIZER)) {
            log.debug("Adding finalizer '{}' to configmap '{}/{}'", PATHFINDER_FINALIZER,
                    configMap.getMetadata().getNamespace(), configMap.getMetadata().getName());
            return Optional.of(cm -> {
                cm.addFinalizer(PATHFINDER_FINALIZER);
                handlers().forEach(handler -> {
                    handler.writeValue(cm.getMetadata(), ingressConfig.getMetadata().getAnnotations());
                });
                return cm;
            });
        }
        return Optional.empty();
    }

    public Optional<UnaryOperator<ConfigMap>> unlinkFromIngress(ConfigMap configMap) {
        if (configMap.hasFinalizer(PATHFINDER_FINALIZER)) {
            log.debug("Removing finalizer '{}' from configmap '{}/{}'", PATHFINDER_FINALIZER,
                    configMap.getMetadata().getNamespace(), configMap.getMetadata().getName());
            return Optional.of(cm -> {
                cm.removeFinalizer(PATHFINDER_FINALIZER);
                return cm;
            });
        }
        return Optional.empty();
    }

    @RequiredArgsConstructor
    private static class AnnotationHandler {

        private final String key;
        private final AnnotationCopySpec copySpec;

        public Map.Entry<String, String> readValue(ObjectMeta objectMeta) {
            var configMapValue = objectMeta.getAnnotations().get(key);
            if (configMapValue == null) {
                log.info("Using default annotation '{}={}' for configmap '{}/{}' as no annotation is configured",
                        key,
                        copySpec.getDefaultValue(),
                        objectMeta.getNamespace(), objectMeta.getName()
                );
                configMapValue = copySpec.getDefaultValue();
            } else if (!copySpec.getAcceptableValues().isEmpty() && !copySpec.getAcceptableValues()
                    .contains(configMapValue)) {
                log.info("Using default annotation '{}={}' for configmap '{}/{}' as value '{}' is not acceptable",
                        key,
                        copySpec.getDefaultValue(),
                        objectMeta.getNamespace(), objectMeta.getName(),
                        configMapValue
                );
                configMapValue = copySpec.getDefaultValue();
            }
            return Map.entry(key, configMapValue);
        }

        public void writeValue(ObjectMeta objectMeta, Map<String, String> values) {
            var toWrite = values.get(key);
            if (toWrite == null) {
                objectMeta.getAnnotations().remove(key);
            } else {
                objectMeta.getAnnotations().put(key, toWrite);
            }
        }
    }

}
