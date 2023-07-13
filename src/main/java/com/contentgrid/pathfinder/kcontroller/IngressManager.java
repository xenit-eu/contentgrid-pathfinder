package com.contentgrid.pathfinder.kcontroller;

import com.contentgrid.pathfinder.config.PathfinderProperties.PathfinderTargetProperties;
import com.contentgrid.pathfinder.kcontroller.events.CreatedEvent;
import com.contentgrid.pathfinder.kcontroller.events.DeletedEvent;
import com.contentgrid.pathfinder.kcontroller.events.Event;
import com.contentgrid.pathfinder.kcontroller.events.ModifiedEvent;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class IngressManager {
    private final PathfinderTargetProperties targetProperties;
    private final IngressGenerator generator;
    private final KubernetesClient kubernetesClient;
    private final ObservationRegistry observationRegistry;

    private static final String PATHFINDER_FINALIZER = "pathfinder.contentgrid.com/ingress";

    public void handleEvent(Event event) {
        try {
            Observation.createNotStarted("pathfinder.kcontroller.handle", observationRegistry)
                    .lowCardinalityKeyValue("event", event.getClass().getSimpleName())
                    .highCardinalityKeyValue("configmap-name", event.getName())
                    .observe(() -> {
                        if(event instanceof CreatedEvent createdEvent) {
                            createOrUpdate(createdEvent.configMap());
                        } else if(event instanceof ModifiedEvent modifiedEvent) {
                            if(isRelevantChange(modifiedEvent.oldConfigMap(), modifiedEvent.newConfigMap())) {
                                createOrUpdate(modifiedEvent.newConfigMap());
                            } else {
                                log.debug("Dropped irrelevant change from '{}' to '{}'", modifiedEvent.oldConfigMap(), modifiedEvent.newConfigMap());
                            }
                        } else if(event instanceof DeletedEvent deletedEvent) {
                            delete(deletedEvent.configMap());
                        } else {
                            throw new RuntimeException("Unknown event kind '%s'".formatted(event.getClass().getSimpleName()));
                        }
                    });
        } catch(Exception exception) {
            log.error("Error during handle of event {} '{}'",
                    event.getClass().getSimpleName(),
                    event.getName(),
                    exception
            );
        }
    }

    private boolean isRelevantChange(ConfigMap oldConfigMap, ConfigMap newConfigMap) {
        var oldIngress = generator.createIngress(oldConfigMap);
        var newIngress = generator.createIngress(newConfigMap);
        return !Objects.equals(oldIngress, newIngress);
    }

    private void createOrUpdate(ConfigMap configMap) {
        generator.createIngress(configMap)
                .ifPresentOrElse(ingressConfig -> {
                    if(!configMap.hasFinalizer(PATHFINDER_FINALIZER)) {
                        log.debug("Adding finalizer '{}' to configmap '{}/{}'", PATHFINDER_FINALIZER, configMap.getMetadata().getNamespace(), configMap.getMetadata().getName());
                        kubernetesClient.configMaps()
                                .resource(configMap)
                                .edit(configMap1 -> {
                                    configMap1.addFinalizer(PATHFINDER_FINALIZER);
                                    return configMap1;
                                });
                    }
                    AtomicBoolean hasEdited = new AtomicBoolean(false);
                    kubernetesClient.network().v1().ingresses()
                            .inNamespace(ingressConfig.getMetadata().getNamespace())
                            .withLabels(generator.labelsFor(configMap))
                            .resources()
                            .forEachOrdered(ingressResource -> {
                                ingressResource.edit(ingress -> {
                                    log.debug("Updating ingress '{}/{}' to new version", ingress.getMetadata().getNamespace(), ingress.getMetadata().getName());
                                    ingress.getMetadata().setLabels(ingressConfig.getMetadata().getLabels());
                                    ingress.getMetadata().setAnnotations(ingressConfig.getMetadata().getAnnotations());
                                    if(ingressConfig.getSpec().getIngressClassName() == null) {
                                        ingressConfig.getSpec().setIngressClassName(ingress.getSpec().getIngressClassName());
                                    }
                                    ingress.setSpec(ingressConfig.getSpec());
                                    return ingress;
                                });
                                hasEdited.set(true);
                            });
                    if(!hasEdited.get()) {
                        // If we did not get to edit any ingress, create a new one
                        var ingress = kubernetesClient.network().v1().ingresses()
                                .resource(ingressConfig)
                                .create();
                        log.info("Created ingress '{}/{}'", ingress.getMetadata().getNamespace(), ingress.getMetadata().getName());
                    }
                }, () -> {
                    log.info("No ingress for configmap '{}/{}', cleaning up", configMap.getMetadata().getNamespace(), configMap.getMetadata().getName());
                    this.delete(configMap);
                });
    }

    private void delete(ConfigMap configMap) {
        var statuses = kubernetesClient.network().v1().ingresses()
                .inNamespace(targetProperties.getNamespace())
                .withLabels(generator.labelsFor(configMap))
                .delete();
        statuses.forEach(statusDetails -> {
            log.info("Removed ingress '{}/{}'", targetProperties.getNamespace(), statusDetails.getName());
        });
        if(configMap.hasFinalizer(PATHFINDER_FINALIZER)) {
            log.debug("Removing finalizer '{}' from configmap '{}/{}'", PATHFINDER_FINALIZER,
                    configMap.getMetadata().getNamespace(), configMap.getMetadata().getName());
            kubernetesClient.configMaps()
                    .resource(configMap)
                    .edit(cm -> {
                        cm.removeFinalizer(PATHFINDER_FINALIZER);
                        return cm;
                    });
        }
    }

}
