package com.contentgrid.pathfinder.kcontroller;

import com.contentgrid.pathfinder.config.PathfinderProperties.PathfinderSourceProperties;
import com.contentgrid.pathfinder.kcontroller.events.CreatedEvent;
import com.contentgrid.pathfinder.kcontroller.events.DeletedEvent;
import com.contentgrid.pathfinder.kcontroller.events.Event;
import com.contentgrid.pathfinder.kcontroller.events.ModifiedEvent;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
@Slf4j
public class ConfigMapWatcher {
    private final PathfinderSourceProperties sourceProperties;
    private final KubernetesClient kubernetesClient;

    public Flux<Event> watch() {
        return Flux.create(emitter -> {
            var informer = kubernetesClient.configMaps()
                    .inNamespace(sourceProperties.getNamespace())
                    .withLabels(sourceProperties.getLabels())
                    .inform(new ResourceEventHandler<ConfigMap>() {
                        @Override
                        public void onAdd(ConfigMap obj) {
                            if(obj.isMarkedForDeletion()) {
                                emitter.next(new DeletedEvent(obj));
                            } else {
                                emitter.next(new CreatedEvent(obj));
                            }
                        }

                        @Override
                        public void onUpdate(ConfigMap oldObj, ConfigMap newObj) {
                            if(!oldObj.isMarkedForDeletion() && newObj.isMarkedForDeletion()) {
                                emitter.next(new DeletedEvent(newObj));
                            } else {
                                emitter.next(new ModifiedEvent(oldObj, newObj));
                            }
                        }

                        @Override
                        public void onDelete(ConfigMap obj, boolean deletedFinalStateUnknown) {
                            emitter.next(new DeletedEvent(obj));
                        }
                    });
            log.info("Watching configmaps in namespace '{}' with labels {}", sourceProperties.getNamespace(), sourceProperties.getLabels());

            emitter.onDispose(informer::close);
        });

    }

}
