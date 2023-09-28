package com.contentgrid.pathfinder.kcontroller;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.pathfinder.config.PathfinderProperties.AnnotationCopySpec;
import com.contentgrid.pathfinder.config.PathfinderProperties.PathfinderTargetProperties;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConfigMapManagerTest {

    private final static PathfinderTargetProperties PROPERTIES = PathfinderTargetProperties.builder()
            .annotation("static", "static-value")
            .copyAnnotation("dynamic-1", AnnotationCopySpec.builder()
                    .defaultValue("dynamic-1-default-value")
                    .build())
            .copyAnnotation("dynamic-2", AnnotationCopySpec.builder()
                    .defaultValue("dynamic-2-default-value")
                    .acceptableValues(Set.of("dynamic-2-option1", "dynamic-2-option2"))
                    .build())
            .build();

    private final static ConfigMapManager CONFIG_MAP_MANAGER = new ConfigMapManager(PROPERTIES);

    @Test
    void annotationValuesReadFromEmptyConfigmap() {
        var configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .endMetadata()
                .build();

        assertThat(CONFIG_MAP_MANAGER.getIngressAnnotations(configMap))
                .isEqualTo(Map.of(
                        "static", "static-value",
                        "dynamic-1", "dynamic-1-default-value",
                        "dynamic-2", "dynamic-2-default-value"
                ));
    }

    @Test
    void annotationValuesReadFromConfigmapWithAnnotations() {
        var configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .addToAnnotations("dynamic-1", "dynamic-1-other-value")
                .addToAnnotations("dynamic-2", "dynamic-2-option1")
                .endMetadata()
                .build();

        assertThat(CONFIG_MAP_MANAGER.getIngressAnnotations(configMap))
                .isEqualTo(Map.of(
                        "static", "static-value",
                        "dynamic-1", "dynamic-1-other-value",
                        "dynamic-2", "dynamic-2-option1"
                ));

    }

    @Test
    void annotationValuesReadFromConfigmapWithInvalidAnnotations() {
        var configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .addToAnnotations("dynamic-2", "invalid-value")
                .endMetadata()
                .build();

        assertThat(CONFIG_MAP_MANAGER.getIngressAnnotations(configMap))
                .isEqualTo(Map.of(
                        "static", "static-value",
                        "dynamic-1", "dynamic-1-default-value",
                        "dynamic-2", "dynamic-2-default-value"
                ));
    }

    @Test
    void linkToIngress() {
        var configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .endMetadata()
                .build();
        var ingress = new IngressBuilder()
                .withNewMetadata()
                .addToAnnotations(CONFIG_MAP_MANAGER.getIngressAnnotations(configMap))
                .endMetadata()
                .build();

        assertThat(CONFIG_MAP_MANAGER.linkToIngress(configMap, ingress)).hasValueSatisfying(modifier -> {
            assertThat(modifier.apply(configMap)).satisfies(newConfigMap -> {
                assertThat(newConfigMap.hasFinalizer(ConfigMapManager.PATHFINDER_FINALIZER)).isTrue();
                assertThat(newConfigMap.getMetadata().getAnnotations()).isEqualTo(
                        Map.of(
                                "dynamic-1", "dynamic-1-default-value",
                                "dynamic-2", "dynamic-2-default-value"
                        )
                );
            });
        });

    }

    @Test
    void linkToIngressWithNonDefaultValues() {
        var configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .addToAnnotations("dynamic-1", "dynamic-1-other-value")
                .addToAnnotations("dynamic-2", "dynamic-2-option1")
                .endMetadata()
                .build();
        var ingress = new IngressBuilder()
                .withNewMetadata()
                .addToAnnotations(CONFIG_MAP_MANAGER.getIngressAnnotations(configMap))
                .endMetadata()
                .build();

        assertThat(CONFIG_MAP_MANAGER.linkToIngress(configMap, ingress)).hasValueSatisfying(modifier -> {
            assertThat(modifier.apply(configMap)).satisfies(newConfigMap -> {
                assertThat(newConfigMap.hasFinalizer(ConfigMapManager.PATHFINDER_FINALIZER)).isTrue();
                assertThat(newConfigMap.getMetadata().getAnnotations()).isEqualTo(
                        Map.of(
                                "dynamic-1", "dynamic-1-other-value",
                                "dynamic-2", "dynamic-2-option1"
                        )
                );
            });
        });

    }

    @Test
    void linkToIngressWithInvalidValue() {
        var configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .addToAnnotations("dynamic-2", "invalid-value")
                .endMetadata()
                .build();
        var ingress = new IngressBuilder()
                .withNewMetadata()
                .addToAnnotations(CONFIG_MAP_MANAGER.getIngressAnnotations(configMap))
                .endMetadata()
                .build();

        assertThat(CONFIG_MAP_MANAGER.linkToIngress(configMap, ingress)).hasValueSatisfying(modifier -> {
            assertThat(modifier.apply(configMap)).satisfies(newConfigMap -> {
                assertThat(newConfigMap.hasFinalizer(ConfigMapManager.PATHFINDER_FINALIZER)).isTrue();
                assertThat(newConfigMap.getMetadata().getAnnotations()).isEqualTo(
                        Map.of(
                                "dynamic-1", "dynamic-1-default-value",
                                "dynamic-2", "dynamic-2-default-value"
                        )
                );
            });
        });

    }
}